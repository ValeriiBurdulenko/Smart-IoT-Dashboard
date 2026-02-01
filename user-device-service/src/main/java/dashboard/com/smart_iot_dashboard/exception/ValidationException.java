package dashboard.com.smart_iot_dashboard.exception;

import java.util.Map;

public class ValidationException extends ApiException {
    public ValidationException(String message, String errorCode) {
        super(message, errorCode);
    }

    public ValidationException(String message, String errorCode, Map<String, Object> details) {
        super(message, errorCode, details);
    }
}

