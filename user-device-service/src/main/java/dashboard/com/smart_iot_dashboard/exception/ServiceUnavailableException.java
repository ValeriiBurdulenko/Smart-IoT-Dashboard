package dashboard.com.smart_iot_dashboard.exception;

public class ServiceUnavailableException extends ApiException {
    public ServiceUnavailableException(String message) {
        super(message, "SERVICE_UNAVAILABLE");
    }

    public ServiceUnavailableException(String message, String errorCode) {
        super(message, errorCode);
    }
}