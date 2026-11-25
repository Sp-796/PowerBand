package powerband.repository;

import powerband.config.Constants;
import powerband.model.Command;
import powerband.model.RecordingIndexEntry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

public final class BinaryRecordingRepository implements SongRepository {
    private final ReentrantLock recordingLock = new ReentrantLock();
    private final ReentrantLock indexLock = new ReentrantLock();

    public BinaryRecordingRepository() {
        validateConfiguredPaths();
    }

    @Override
    public RecordingWriter beginRecording(String songName) {
        byte[] encodedName = encodeName(songName);
        recordingLock.lock();
        RandomAccessFile music = null;
        try {
            createParentDirectories(Constants.RECORDINGS_FILE);
            music = new RandomAccessFile(Constants.RECORDINGS_FILE.toFile(), "rw");
            long fileLength = music.length();
            if (fileLength % Constants.MUSIC_RECORD_BYTES != 0) {
                throw new RecordingFormatException("Recordings file length is not aligned to a fixed record");
            }
            long musicOffset = fileLength;
            long nextLength = Math.addExact(musicOffset, Constants.MUSIC_RECORD_BYTES);
            music.setLength(nextLength);
            music.seek(musicOffset);
            writeFixedName(music, encodedName);
            music.writeInt(0);
            return new FileRecordingWriter(music, songName, musicOffset);
        } catch (IOException | ArithmeticException exception) {
            closeAfterFailure(music, exception);
            recordingLock.unlock();
            throw new StorageException("Could not start recording " + songName, exception);
        } catch (RuntimeException exception) {
            closeAfterFailure(music, exception);
            recordingLock.unlock();
            throw exception;
        }
    }

    @Override
    public RecordingReader openReader(String songName) {
        encodeName(songName);
        RecordingIndexEntry entry = findEntry(songName);
        RandomAccessFile music = null;
        try {
            music = new RandomAccessFile(Constants.RECORDINGS_FILE.toFile(), "r");
            long musicLength = music.length();
            if (musicLength % Constants.MUSIC_RECORD_BYTES != 0) {
                throw new RecordingFormatException("Recordings file length is not aligned to a fixed record");
            }
            if (entry.musicOffset() % Constants.MUSIC_RECORD_BYTES != 0
                    || entry.musicOffset() > musicLength - Constants.MUSIC_RECORD_BYTES) {
                throw new IndexCorruptedException("Recording index points outside an aligned music record");
            }
            music.seek(entry.musicOffset());
            String storedName = readFixedName(music);
            if (!storedName.equals(entry.songName())) {
                throw new IndexCorruptedException("Index title does not match the referenced recording");
            }
            int commandCount = music.readInt();
            if (commandCount < 0 || commandCount > Constants.MAX_COMMANDS_PER_RECORD) {
                throw new RecordingFormatException("Command count is outside the configured record capacity");
            }
            long commandsOffset = entry.musicOffset() + Constants.TITLE_BYTES + Constants.COUNT_BYTES;
            music.seek(commandsOffset);
            return new FileRecordingReader(music, storedName, commandCount);
        } catch (IOException exception) {
            closeAfterFailure(music, exception);
            throw new StorageException("Could not open recording " + songName, exception);
        } catch (RuntimeException exception) {
            closeAfterFailure(music, exception);
            throw exception;
        }
    }

    private RecordingIndexEntry findEntry(String songName) {
        indexLock.lock();
        try {
            if (!Files.exists(Constants.INDEX_FILE)) {
                throw new RecordingNotFoundException(songName);
            }
            try (RandomAccessFile index = new RandomAccessFile(Constants.INDEX_FILE.toFile(), "r")) {
                long fileLength = index.length();
                if (fileLength % Constants.INDEX_RECORD_BYTES != 0) {
                    throw new IndexCorruptedException("Index file length is not aligned to a fixed entry");
                }
                long count = fileLength / Constants.INDEX_RECORD_BYTES;
                long low = 0;
                long high = count;
                while (low < high) {
                    long middle = low + (high - low) / 2;
                    index.seek(middle * Constants.INDEX_RECORD_BYTES);
                    RecordingIndexEntry candidate = readIndexEntry(index);
                    if (compareNatural(candidate.songName(), songName) < 0) {
                        low = middle + 1;
                    } else {
                        high = middle;
                    }
                }
                if (low == count) {
                    throw new RecordingNotFoundException(songName);
                }
                index.seek(low * Constants.INDEX_RECORD_BYTES);
                RecordingIndexEntry candidate = readIndexEntry(index);
                if (compareNatural(candidate.songName(), songName) != 0) {
                    throw new RecordingNotFoundException(songName);
                }
                return candidate;
            } catch (IOException exception) {
                throw new StorageException("Could not search recording index", exception);
            }
        } finally {
            indexLock.unlock();
        }
    }

    private void appendIndexEntry(RecordingIndexEntry entry) {
        indexLock.lock();
        try {
            try {
                createParentDirectories(Constants.INDEX_FILE);
            } catch (IOException exception) {
                throw new StorageException("Could not create index directory", exception);
            }

            long originalLength = -1;
            RandomAccessFile index = null;
            try {
                index = new RandomAccessFile(Constants.INDEX_FILE.toFile(), "rw");
                originalLength = index.length();
                if (originalLength % Constants.INDEX_RECORD_BYTES != 0) {
                    throw new IndexCorruptedException("Index file length is not aligned to a fixed entry");
                }
                index.seek(originalLength);
                writeIndexEntry(index, entry);
                index.close();
                index = null;
                sortIndex();
            } catch (IOException exception) {
                rollbackIndex(index, originalLength, exception);
                throw new StorageException("Could not append recording index entry", exception);
            } catch (RuntimeException exception) {
                rollbackIndex(index, originalLength, exception);
                throw exception;
            }
        } finally {
            indexLock.unlock();
        }
    }

    private void sortIndex() {
        boolean backupCreated = false;
        try {
            long fileLength;
            try (RandomAccessFile index = new RandomAccessFile(Constants.INDEX_FILE.toFile(), "r")) {
                fileLength = index.length();
            }
            if (fileLength % Constants.INDEX_RECORD_BYTES != 0) {
                throw new IndexCorruptedException("Index file length is not aligned to a fixed entry");
            }
            long recordCount = fileLength / Constants.INDEX_RECORD_BYTES;
            if (recordCount > 1) {
                createParentDirectories(Constants.INDEX_TEMP_A);
                createParentDirectories(Constants.INDEX_TEMP_B);
                Files.copy(Constants.INDEX_FILE, Constants.INDEX_TEMP_A,
                        StandardCopyOption.REPLACE_EXISTING);
                backupCreated = true;
                long runLength = 1;
                while (runLength < recordCount) {
                    mergeRuns(runLength, recordCount);
                    runLength = runLength > recordCount / 2 ? recordCount : runLength * 2;
                }
                Files.deleteIfExists(Constants.INDEX_TEMP_B);
                Files.delete(Constants.INDEX_TEMP_A);
            }
        } catch (IOException exception) {
            throw recoverIndexAfterSortFailure(exception, backupCreated);
        } catch (StorageException exception) {
            throw recoverIndexAfterSortFailure(exception, backupCreated);
        }
    }

    private void mergeRuns(long runLength, long recordCount) throws IOException {
        try (RandomAccessFile source = new RandomAccessFile(Constants.INDEX_FILE.toFile(), "r");
             RandomAccessFile target = new RandomAccessFile(Constants.INDEX_TEMP_B.toFile(), "rw")) {
            target.setLength(0);
            long position = 0;
            while (position < recordCount) {
                long middle = Math.min(position + runLength, recordCount);
                long end = Math.min(middle + runLength, recordCount);
                long leftPosition = position;
                long rightPosition = middle;
                RecordingIndexEntry left = leftPosition < middle
                        ? readIndexEntryAt(source, leftPosition++) : null;
                RecordingIndexEntry right = rightPosition < end
                        ? readIndexEntryAt(source, rightPosition++) : null;

                while (left != null || right != null) {
                    if (left == null) {
                        writeIndexEntry(target, right);
                        right = rightPosition < end
                                ? readIndexEntryAt(source, rightPosition++) : null;
                    } else if (right == null) {
                        writeIndexEntry(target, left);
                        left = leftPosition < middle
                                ? readIndexEntryAt(source, leftPosition++) : null;
                    } else if (compareEntries(left, right) <= 0) {
                        writeIndexEntry(target, left);
                        left = leftPosition < middle
                                ? readIndexEntryAt(source, leftPosition++) : null;
                    } else {
                        writeIndexEntry(target, right);
                        right = rightPosition < end
                                ? readIndexEntryAt(source, rightPosition++) : null;
                    }
                }
                position = end;
            }
        }

        try (RandomAccessFile source = new RandomAccessFile(Constants.INDEX_TEMP_B.toFile(), "r");
             RandomAccessFile target = new RandomAccessFile(Constants.INDEX_FILE.toFile(), "rw")) {
            target.setLength(0);
            for (long position = 0; position < recordCount; position++) {
                writeIndexEntry(target, readIndexEntry(source));
            }
        }
    }

    private static StorageException recoverIndexAfterSortFailure(Throwable failure, boolean backupCreated) {
        StorageException sortFailure = failure instanceof StorageException
                ? (StorageException) failure
                : new StorageException("Could not sort recording index on disk", failure);
        if (backupCreated) {
            try {
                Files.copy(Constants.INDEX_TEMP_A, Constants.INDEX_FILE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException restoreException) {
                sortFailure.addSuppressed(restoreException);
            }
        }
        deleteSortFile(Constants.INDEX_TEMP_B, sortFailure);
        deleteSortFile(Constants.INDEX_TEMP_A, sortFailure);
        return sortFailure;
    }

    private static void deleteSortFile(Path path, StorageException failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    private static RecordingIndexEntry readIndexEntryAt(RandomAccessFile source, long position)
            throws IOException {
        source.seek(position * Constants.INDEX_RECORD_BYTES);
        return readIndexEntry(source);
    }

    private int compareEntries(RecordingIndexEntry left, RecordingIndexEntry right) {
        int nameOrder = compareNatural(left.songName(), right.songName());
        if (nameOrder != 0) {
            return nameOrder;
        }
        int originalNameOrder = left.songName().compareTo(right.songName());
        if (originalNameOrder != 0) {
            return originalNameOrder;
        }
        return Long.compare(left.musicOffset(), right.musicOffset());
    }

    private static int compareNatural(String left, String right) {
        String normalizedLeft = normalizeName(left);
        String normalizedRight = normalizeName(right);
        int leftPosition = 0;
        int rightPosition = 0;
        while (leftPosition < normalizedLeft.length() && rightPosition < normalizedRight.length()) {
            char leftCharacter = normalizedLeft.charAt(leftPosition);
            char rightCharacter = normalizedRight.charAt(rightPosition);
            if (isAsciiDigit(leftCharacter) && isAsciiDigit(rightCharacter)) {
                int leftEnd = digitEnd(normalizedLeft, leftPosition);
                int rightEnd = digitEnd(normalizedRight, rightPosition);
                int leftSignificant = significantDigitStart(normalizedLeft, leftPosition, leftEnd);
                int rightSignificant = significantDigitStart(normalizedRight, rightPosition, rightEnd);
                int leftDigits = leftEnd - leftSignificant;
                int rightDigits = rightEnd - rightSignificant;
                if (leftDigits != rightDigits) {
                    return Integer.compare(leftDigits, rightDigits);
                }
                int numberOrder = normalizedLeft.substring(leftSignificant, leftEnd)
                        .compareTo(normalizedRight.substring(rightSignificant, rightEnd));
                if (numberOrder != 0) {
                    return numberOrder;
                }
                leftPosition = leftEnd;
                rightPosition = rightEnd;
            } else {
                if (leftCharacter != rightCharacter) {
                    return Character.compare(leftCharacter, rightCharacter);
                }
                leftPosition++;
                rightPosition++;
            }
        }
        return Integer.compare(normalizedLeft.length() - leftPosition,
                normalizedRight.length() - rightPosition);
    }

    private static int digitEnd(String value, int start) {
        int end = start;
        while (end < value.length() && isAsciiDigit(value.charAt(end))) {
            end++;
        }
        return end;
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static int significantDigitStart(String value, int start, int end) {
        int position = start;
        while (position < end - 1 && value.charAt(position) == '0') {
            position++;
        }
        return position;
    }

    private static String normalizeName(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(decomposed.length());
        for (int position = 0; position < decomposed.length(); position++) {
            char character = decomposed.charAt(position);
            int category = Character.getType(character);
            if (category != Character.NON_SPACING_MARK
                    && category != Character.COMBINING_SPACING_MARK
                    && category != Character.ENCLOSING_MARK) {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    private static RecordingIndexEntry readIndexEntry(RandomAccessFile source) throws IOException {
        String songName = readFixedName(source);
        long musicOffset = source.readLong();
        try {
            return new RecordingIndexEntry(songName, musicOffset);
        } catch (IllegalArgumentException exception) {
            throw new IndexCorruptedException("Invalid recording index entry", exception);
        }
    }

    private static void writeIndexEntry(RandomAccessFile target, RecordingIndexEntry entry) throws IOException {
        writeFixedName(target, encodeName(entry.songName()));
        target.writeLong(entry.musicOffset());
    }

    private static void writeFixedName(RandomAccessFile target, byte[] encodedName) throws IOException {
        target.write(encodedName);
        target.write(new byte[Constants.TITLE_BYTES - encodedName.length]);
    }

    private static String readFixedName(RandomAccessFile source) throws IOException {
        byte[] bytes = new byte[Constants.TITLE_BYTES];
        source.readFully(bytes);
        int nameLength = 0;
        while (nameLength < bytes.length && bytes[nameLength] != 0) {
            nameLength++;
        }
        for (int position = nameLength; position < bytes.length; position++) {
            if (bytes[position] != 0) {
                throw new RecordingFormatException("Fixed-width title has non-zero data after its terminator");
            }
        }
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            String name = decoder.decode(ByteBuffer.wrap(bytes, 0, nameLength)).toString();
            if (name.isEmpty()) {
                throw new RecordingFormatException("Stored song title is empty");
            }
            return name;
        } catch (CharacterCodingException exception) {
            throw new RecordingFormatException("Stored song title is not valid UTF-8", exception);
        }
    }

    private static byte[] encodeName(String songName) {
        if (songName == null || songName.isEmpty() || songName.indexOf('\0') >= 0) {
            throw new RecordingFormatException("Song title must be non-empty and cannot contain the reserved terminator");
        }
        try {
            CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer encoded = encoder.encode(CharBuffer.wrap(songName));
            if (encoded.remaining() > Constants.TITLE_BYTES) {
                throw new RecordingFormatException("Song title exceeds the configured UTF-8 field width");
            }
            byte[] result = new byte[encoded.remaining()];
            encoded.get(result);
            return result;
        } catch (CharacterCodingException exception) {
            throw new RecordingFormatException("Song title cannot be encoded as UTF-8", exception);
        }
    }

    private static void createParentDirectories(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static void validateConfiguredPaths() {
        Path music = Constants.RECORDINGS_FILE.toAbsolutePath().normalize();
        Path index = Constants.INDEX_FILE.toAbsolutePath().normalize();
        Path first = Constants.INDEX_TEMP_A.toAbsolutePath().normalize();
        Path second = Constants.INDEX_TEMP_B.toAbsolutePath().normalize();
        if (music.equals(index) || music.equals(first) || music.equals(second)
                || index.equals(first) || index.equals(second) || first.equals(second)) {
            throw new IllegalArgumentException("Recording, index, and temporary paths must be distinct");
        }
    }

    private static void closeAfterFailure(RandomAccessFile file, Throwable failure) {
        if (file != null) {
            try {
                file.close();
            } catch (IOException closeException) {
                failure.addSuppressed(closeException);
            }
        }
    }

    private static void rollbackIndex(RandomAccessFile index, long originalLength, Throwable failure) {
        if (originalLength < 0) {
            return;
        }
        if (index != null) {
            try {
                index.setLength(originalLength);
            } catch (IOException rollbackException) {
                failure.addSuppressed(rollbackException);
            }
            try {
                index.close();
            } catch (IOException rollbackException) {
                failure.addSuppressed(rollbackException);
            }
        } else {
            try (RandomAccessFile rollback = new RandomAccessFile(Constants.INDEX_FILE.toFile(), "rw")) {
                rollback.setLength(originalLength);
            } catch (IOException rollbackException) {
                failure.addSuppressed(rollbackException);
            }
        }
    }

    private final class FileRecordingWriter implements RecordingWriter {
        private RandomAccessFile music;
        private final String songName;
        private final long musicOffset;
        private int commandCount;
        private boolean closed;
        private StorageException writeFailure;

        private FileRecordingWriter(RandomAccessFile music, String songName, long musicOffset) {
            this.music = music;
            this.songName = songName;
            this.musicOffset = musicOffset;
        }

        @Override
        public void append(Command command) {
            if (command == null) {
                throw new IllegalArgumentException("Command is required");
            }
            ensureWritable();
            if (commandCount == Constants.MAX_COMMANDS_PER_RECORD) {
                writeFailure = new RecordingFormatException(
                        "Recording exceeds the configured command capacity");
                throw writeFailure;
            }
            try {
                long commandOffset = musicOffset + Constants.TITLE_BYTES + Constants.COUNT_BYTES
                        + (long) commandCount * Constants.COMMAND_BYTES;
                music.seek(commandOffset);
                writeCommand(music, command);
                commandCount++;
            } catch (IOException exception) {
                writeFailure = new StorageException("Could not write command to recording " + songName, exception);
                throw writeFailure;
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            StorageException failure = null;
            try {
                if (writeFailure != null) {
                    throw writeFailure;
                }
                music.seek(musicOffset + Constants.TITLE_BYTES);
                music.writeInt(commandCount);
                music.close();
                music = null;
                appendIndexEntry(new RecordingIndexEntry(songName, musicOffset));
            } catch (IOException exception) {
                failure = new StorageException("Could not finish recording " + songName, exception);
            } catch (StorageException exception) {
                failure = exception;
            } finally {
                try {
                    if (music != null) {
                        music.close();
                        music = null;
                    }
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = new StorageException("Could not close recording file", exception);
                    } else {
                        failure.addSuppressed(exception);
                    }
                } finally {
                    recordingLock.unlock();
                }
            }
            if (failure != null) {
                writeFailure = failure;
                throw failure;
            }
        }

        private void ensureWritable() {
            if (closed) {
                throw new StorageException("Recording writer is already closed");
            }
            if (writeFailure != null) {
                throw writeFailure;
            }
        }
    }

    private static void writeCommand(RandomAccessFile target, Command command) throws IOException {
        target.writeLong(command.elapsedNanos());
        target.writeLong(command.sequence());
        target.writeInt(command.synthId());
        target.writeByte(command.type().code());
        target.writeInt(command.midiNote());
        target.writeInt(command.velocity());
    }

    private static Command readCommand(RandomAccessFile source) throws IOException {
        long elapsedNanos = source.readLong();
        long sequence = source.readLong();
        int synthId = source.readInt();
        byte commandCode = source.readByte();
        int midiNote = source.readInt();
        int velocity = source.readInt();
        try {
            return new Command(elapsedNanos, sequence, synthId, Command.Type.fromCode(commandCode),
                    midiNote, velocity);
        } catch (IllegalArgumentException exception) {
            throw new RecordingFormatException("Stored command contains invalid fields", exception);
        }
    }

    private static final class FileRecordingReader implements RecordingReader {
        private RandomAccessFile music;
        private final String songName;
        private final int commandCount;
        private int commandsRead;

        private FileRecordingReader(RandomAccessFile music, String songName, int commandCount) {
            this.music = music;
            this.songName = songName;
            this.commandCount = commandCount;
        }

        @Override
        public String songName() {
            return songName;
        }

        @Override
        public int commandCount() {
            return commandCount;
        }

        @Override
        public Command readNext() {
            if (music == null) {
                throw new StorageException("Recording reader is closed");
            }
            if (commandsRead == commandCount) {
                return null;
            }
            try {
                Command command = readCommand(music);
                commandsRead++;
                return command;
            } catch (IOException exception) {
                throw new StorageException("Could not read command from recording " + songName, exception);
            }
        }

        @Override
        public void close() {
            if (music == null) {
                return;
            }
            try {
                music.close();
                music = null;
            } catch (IOException exception) {
                throw new StorageException("Could not close recording reader " + songName, exception);
            }
        }
    }
}
