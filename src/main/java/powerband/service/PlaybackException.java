package powerband.service;

public class PlaybackException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PlaybackException(String message, Throwable cause) {
        super(message, cause);
    }

    public PlaybackException(String message) {
        super(message);
    }
}
