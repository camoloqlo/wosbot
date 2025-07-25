package cl.camodev.wosbot.ex;

public class ADBConnectionException extends RuntimeException {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public ADBConnectionException(String message) {
        super(message);
    }

    public ADBConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ADBConnectionException(Throwable cause) {
        super(cause);
    }
}
