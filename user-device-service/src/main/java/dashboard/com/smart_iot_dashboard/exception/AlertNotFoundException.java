package dashboard.com.smart_iot_dashboard.exception;

public class AlertNotFoundException extends ResourceNotFoundException {
    public AlertNotFoundException(String alertId) {
        super("Alert not found: " + alertId, "ALERT_NOT_FOUND");
    }
}
