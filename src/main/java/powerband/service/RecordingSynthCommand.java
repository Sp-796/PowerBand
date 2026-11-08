package powerband.service;

import powerband.config.Constants;
import powerband.model.Command;
import powerband.model.SynthCommand;
import powerband.repository.RecordingWriter;
import powerband.repository.SongRepository;
import powerband.repository.StorageException;

import java.util.function.LongSupplier;

public final class RecordingSynthCommand implements SynthCommand {
    private final SynthCommand delegate;
    private final SongRepository songRepository;
    private final LongSupplier monotonicClock;
    private RecordingWriter writer;
    private long startedAtNanos;
    private long nextSequence;
    private StorageException recordingFailure;

    public RecordingSynthCommand(SynthCommand delegate, SongRepository songRepository) {
        this(delegate, songRepository, System::nanoTime);
    }

    public RecordingSynthCommand(SynthCommand delegate, SongRepository songRepository,
                                 LongSupplier monotonicClock) {
        if (delegate == null || songRepository == null || monotonicClock == null) {
            throw new IllegalArgumentException("Synth command, repository, and clock are required");
        }
        this.delegate = delegate;
        this.songRepository = songRepository;
        this.monotonicClock = monotonicClock;
    }

    public synchronized void startRecording(String songName) {
        if (writer != null) {
            throw new StorageException("A recording session is already active");
        }
        recordingFailure = null;
        long startTime = monotonicClock.getAsLong();
        RecordingWriter newWriter = songRepository.beginRecording(songName);
        startedAtNanos = startTime;
        nextSequence = Constants.FIRST_SEQUENCE;
        writer = newWriter;
    }

    public synchronized void stopRecording() {
        if (writer == null) {
            if (recordingFailure != null) {
                throw recordingFailure;
            }
            throw new StorageException("No recording session is active");
        }
        RecordingWriter completedWriter = writer;
        writer = null;
        try {
            completedWriter.close();
        } catch (StorageException exception) {
            recordingFailure = exception;
            throw exception;
        }
    }

    public synchronized boolean isRecording() {
        return writer != null;
    }

    @Override
    public int synthId() {
        return delegate.synthId();
    }

    @Override
    public synchronized void noteOn(int midiNote, int velocity) {
        long elapsedNanos = captureTimestamp();
        delegate.noteOn(midiNote, velocity);
        appendIfRecording(elapsedNanos, Command.Type.NOTE_ON, midiNote, velocity);
    }

    @Override
    public synchronized void noteOff(int midiNote) {
        long elapsedNanos = captureTimestamp();
        delegate.noteOff(midiNote);
        appendIfRecording(elapsedNanos, Command.Type.NOTE_OFF, midiNote, Constants.MIN_MIDI_VALUE);
    }

    private long captureTimestamp() {
        if (writer == null) {
            return Constants.NO_RECORDING_TIMESTAMP;
        }
        long elapsedNanos = monotonicClock.getAsLong() - startedAtNanos;
        if (elapsedNanos < 0) {
            throw new StorageException("Monotonic clock moved backwards during recording");
        }
        return elapsedNanos;
    }

    private void appendIfRecording(long elapsedNanos, Command.Type type, int midiNote, int velocity) {
        if (writer == null || recordingFailure != null) {
            return;
        }
        if (nextSequence == Long.MAX_VALUE) {
            recordingFailure = new StorageException("Recording command sequence capacity was exceeded");
            failWriter(recordingFailure);
            throw recordingFailure;
        }
        try {
            writer.append(new Command(elapsedNanos, nextSequence, delegate.synthId(), type, midiNote, velocity));
            nextSequence++;
        } catch (StorageException exception) {
            recordingFailure = exception;
            failWriter(exception);
            throw exception;
        }
    }

    private void failWriter(StorageException failure) {
        RecordingWriter failedWriter = writer;
        writer = null;
        try {
            failedWriter.close();
        } catch (StorageException closeException) {
            failure.addSuppressed(closeException);
        }
    }
}
