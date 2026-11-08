package powerband.model;

public final class SynthCommandException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SynthCommandException(String message, Throwable cause) {
        super(message, cause);
    }

    public SynthCommandException(String message) {
        super(message);
    }
}
