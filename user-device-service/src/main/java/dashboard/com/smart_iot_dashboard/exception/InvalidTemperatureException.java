package dashboard.com.smart_iot_dashboard.exception;

import java.util.Map;

public class InvalidTemperatureException extends ValidationException {
    public InvalidTemperatureException(String message, double min, double max) {
        super(message, "INVALID_TEMPERATURE_RANGE",
                Map.of("minAllowed", min, "maxAllowed", max));
    }
}
