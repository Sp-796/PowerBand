package powerband.model;

public final class HardwareInputException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public HardwareInputException(String message) {
        super(message);
    }

    public HardwareInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
