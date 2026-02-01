package dashboard.com.smart_iot_dashboard.exception;

public class InternalServerException extends ApiException {
    public InternalServerException(String message) {
        super(message, "INTERNAL_SERVER_ERROR");
    }

    public InternalServerException(String message, Throwable cause) {
        super(message + ": " + cause.getMessage(), "INTERNAL_SERVER_ERROR");
        initCause(cause);
    }
}