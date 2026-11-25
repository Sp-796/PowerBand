package powerband;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import powerband.config.Constants;
import powerband.model.Instrument;
import powerband.model.SynthCommand;
import powerband.repository.BinaryRecordingRepository;
import powerband.repository.RecordingNotFoundException;
import powerband.repository.RecordingReader;
import powerband.repository.RecordingWriter;
import powerband.repository.SongRepository;
import powerband.repository.StorageException;
import powerband.service.MusicEditorService;
import powerband.service.PlaybackException;
import powerband.service.RecordingSynthCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PowerBandIntegrationTest {
    private static Path storageDirectory;

    @BeforeAll
    static void configureStoragePaths() throws IOException {
        storageDirectory = Files.createTempDirectory("powerband-tests");
        System.setProperty("powerband.recordings.file",
                storageDirectory.resolve("recordings.music").toString());
        System.setProperty("powerband.index.file",
                storageDirectory.resolve("recordings.index").toString());
        System.setProperty("powerband.index.tempA",
                storageDirectory.resolve("recordings.index.tmp-a").toString());
        System.setProperty("powerband.index.tempB",
                storageDirectory.resolve("recordings.index.tmp-b").toString());
    }

    @BeforeEach
    void clearStorageFiles() throws IOException {
        Files.deleteIfExists(Constants.RECORDINGS_FILE);
        Files.deleteIfExists(Constants.INDEX_FILE);
        Files.deleteIfExists(Constants.INDEX_TEMP_A);
        Files.deleteIfExists(Constants.INDEX_TEMP_B);
    }

    @AfterAll
    static void removeStorageDirectory() throws IOException {
        if (storageDirectory != null) {
            Files.deleteIfExists(Constants.RECORDINGS_FILE);
            Files.deleteIfExists(Constants.INDEX_FILE);
            Files.deleteIfExists(Constants.INDEX_TEMP_A);
            Files.deleteIfExists(Constants.INDEX_TEMP_B);
            Files.deleteIfExists(storageDirectory);
        }
    }

    @Test
    void failedIndexSortRestoresPreviousEntriesAndRemovesFailedEntry() throws Exception {
        BinaryRecordingRepository repository = new BinaryRecordingRepository();
        try (RecordingWriter ignored = repository.beginRecording("Zeta")) {
        }

        Files.createDirectory(Constants.INDEX_TEMP_B);
        RecordingWriter failedWriter = repository.beginRecording("Alpha");
        assertThrows(StorageException.class, failedWriter::close);

        try (RecordingReader ignored = repository.openReader("Zeta")) {
            assertEquals(Constants.INDEX_RECORD_BYTES, Files.size(Constants.INDEX_FILE));
        }
        assertThrows(RecordingNotFoundException.class, () -> repository.openReader("Alpha"));
    }

    @Test
    void sortedIndexRemainsSearchableAfterMultipleMergePasses() {
        BinaryRecordingRepository repository = new BinaryRecordingRepository();
        writeEmptyRecording(repository, "Song 10");
        writeEmptyRecording(repository, "Song 2");
        writeEmptyRecording(repository, "Song 1");

        assertRecordingExists(repository, "Song 1");
        assertRecordingExists(repository, "Song 2");
        assertRecordingExists(repository, "Song 10");
    }

    @Test
    void playbackRejectionsUsePlaybackException() {
        SongRepository repository = new BinaryRecordingRepository();
        RecordingSynthCommand recordingSynth =
                new RecordingSynthCommand(new TestSynthCommand(), repository);
        MusicEditorService service = new MusicEditorService(repository, recordingSynth);

        assertThrows(PlaybackException.class, () -> service.play("song", null));
        service.close();
        assertThrows(PlaybackException.class,
                () -> service.play("song", new Instrument(new TestSynthCommand())));
        assertThrows(PlaybackException.class, () -> service.startRecording("song"));
    }

    private static final class TestSynthCommand implements SynthCommand {
        @Override
        public int synthId() {
            return 0;
        }

        @Override
        public void noteOn(int midiNote, int velocity) {
        }

        @Override
        public void noteOff(int midiNote) {
        }
    }

    private static void writeEmptyRecording(BinaryRecordingRepository repository, String songName) {
        try (RecordingWriter ignored = repository.beginRecording(songName)) {
        }
    }

    private static void assertRecordingExists(BinaryRecordingRepository repository, String songName) {
        try (RecordingReader ignored = repository.openReader(songName)) {
        }
    }
}
