package dashboard.com.smart_iot_dashboard.exception;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String message, String errorCode) {
        super(message, errorCode);
    }
}

