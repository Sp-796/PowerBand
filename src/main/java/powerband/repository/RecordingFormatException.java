package powerband.repository;

public final class RecordingFormatException extends StorageException {
    private static final long serialVersionUID = 1L;

    public RecordingFormatException(String message) {
        super(message);
    }

    public RecordingFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
