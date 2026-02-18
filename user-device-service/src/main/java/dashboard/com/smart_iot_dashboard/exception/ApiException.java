package dashboard.com.smart_iot_dashboard.exception;

import lombok.Getter;


@Getter
public class ApiException extends RuntimeException {

    private final String errorCode;
    private final Object details;

    public ApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    public ApiException(String message, String errorCode, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
}