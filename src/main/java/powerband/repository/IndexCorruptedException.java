package powerband.repository;

public final class IndexCorruptedException extends StorageException {
    private static final long serialVersionUID = 1L;

    public IndexCorruptedException(String message) {
        super(message);
    }

    public IndexCorruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
