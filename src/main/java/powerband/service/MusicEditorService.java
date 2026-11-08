package powerband.service;

import powerband.config.Constants;
import powerband.model.Command;
import powerband.model.Instrument;
import powerband.repository.RecordingReader;
import powerband.repository.SongRepository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class MusicEditorService implements AutoCloseable {
    private final SongRepository songRepository;
    private final RecordingSynthCommand recordingSynth;
    private final LongSupplier monotonicClock;
    private final ExecutorService playbackExecutor;
    private PlaybackState playbackState;
    private boolean closed;

    public MusicEditorService(SongRepository songRepository, RecordingSynthCommand recordingSynth) {
        this(songRepository, recordingSynth, System::nanoTime);
    }

    public MusicEditorService(SongRepository songRepository, RecordingSynthCommand recordingSynth,
                              LongSupplier monotonicClock) {
        if (songRepository == null || recordingSynth == null || monotonicClock == null) {
            throw new IllegalArgumentException("Repository, recording synth, and clock are required");
        }
        this.songRepository = songRepository;
        this.recordingSynth = recordingSynth;
        this.monotonicClock = monotonicClock;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, Constants.PLAYBACK_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        };
        this.playbackExecutor = Executors.newSingleThreadExecutor(threadFactory);
    }

    public synchronized void startRecording(String songName) {
        ensureOpen();
        recordingSynth.startRecording(songName);
    }

    public synchronized void stopRecording() {
        ensureOpen();
        recordingSynth.stopRecording();
    }

    public synchronized CompletableFuture<Void> play(String songName, Instrument instrument) {
        ensureOpen();
        if (instrument == null) {
            throw new IllegalArgumentException("Playback instrument is required");
        }
        if (playbackState != null && !playbackState.completion.isDone()) {
            throw new PlaybackException("A song is already playing");
        }
        PlaybackState state = new PlaybackState();
        playbackState = state;
        state.task = playbackExecutor.submit(() -> playRecording(songName, instrument, state));
        return state.completion;
    }

    public synchronized void stopPlayback() {
        if (playbackState != null && !playbackState.completion.isDone()) {
            playbackState.completion.completeExceptionally(
                    new PlaybackCancelledException("Playback was stopped"));
            if (playbackState.task != null) {
                playbackState.task.cancel(true);
            }
        }
    }

    private void playRecording(String songName, Instrument instrument, PlaybackState state) {
        try (RecordingReader reader = songRepository.openReader(songName)) {
            long playbackStartedAt = monotonicClock.getAsLong();
            Command command;
            while ((command = reader.readNext()) != null) {
                waitForCommand(playbackStartedAt, command.elapsedNanos());
                if (command.type() == Command.Type.NOTE_ON) {
                    instrument.noteOn(command.midiNote(), command.velocity());
                } else {
                    instrument.noteOff(command.midiNote());
                }
            }
            state.completion.complete(null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            state.completion.completeExceptionally(
                    new PlaybackCancelledException("Playback was interrupted"));
        } catch (RuntimeException exception) {
            state.completion.completeExceptionally(exception);
        } finally {
            synchronized (this) {
                if (playbackState == state) {
                    playbackState = null;
                }
            }
        }
    }

    private void waitForCommand(long playbackStartedAt, long commandElapsedNanos)
            throws InterruptedException {
        while (true) {
            long elapsed = monotonicClock.getAsLong() - playbackStartedAt;
            long remaining = commandElapsedNanos - elapsed;
            if (remaining <= 0) {
                return;
            }
            TimeUnit.NANOSECONDS.sleep(remaining);
        }
    }

    private synchronized void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Music editor service is closed");
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        try {
            stopPlayback();
            if (recordingSynth.isRecording()) {
                recordingSynth.stopRecording();
            }
        } finally {
            playbackExecutor.shutdownNow();
        }
    }

    private static final class PlaybackState {
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private Future<?> task;
    }
}
