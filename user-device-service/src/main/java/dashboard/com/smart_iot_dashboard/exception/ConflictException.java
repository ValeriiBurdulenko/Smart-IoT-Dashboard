package dashboard.com.smart_iot_dashboard.exception;

public class ConflictException extends ApiException {
    public ConflictException(String message, String errorCode) {
        super(message, errorCode);
    }
}

