package dashboard.com.smart_iot_dashboard.exception;

public class DuplicateAlertException extends ConflictException {
    public DuplicateAlertException(String alertId) {
        super("Duplicate alert: " + alertId, "DUPLICATE_ALERT");
    }
}
