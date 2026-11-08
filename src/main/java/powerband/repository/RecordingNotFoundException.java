package powerband.repository;

public final class RecordingNotFoundException extends StorageException {
    private static final long serialVersionUID = 1L;

    public RecordingNotFoundException(String songName) {
        super("Recording not found: " + songName);
    }
}
