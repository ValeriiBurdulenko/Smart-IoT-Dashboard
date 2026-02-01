package dashboard.com.smart_iot_dashboard.exception;

public class AuthenticationException extends ApiException {
    public AuthenticationException(String message, String errorCode) {
        super(message, errorCode);
    }
}

