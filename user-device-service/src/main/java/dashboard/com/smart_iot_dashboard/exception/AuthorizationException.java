package dashboard.com.smart_iot_dashboard.exception;

public class AuthorizationException extends ApiException {
    public AuthorizationException(String message, String errorCode) {
        super(message, errorCode);
    }
}