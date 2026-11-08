package powerband.service;

public final class PlaybackCancelledException extends PlaybackException {
    private static final long serialVersionUID = 1L;

    public PlaybackCancelledException(String message) {
        super(message);
    }
}
