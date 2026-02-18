package dashboard.com.smart_iot_dashboard.exception;

public class AccessDeniedException extends AuthorizationException {
    private AccessDeniedException(String message) {
        super(message, "ACCESS_DENIED");
    }

    public static AccessDeniedException withMessage(String message) {
        return new AccessDeniedException(message);
    }

    public static AccessDeniedException forResource(String resourceId) {
        return new AccessDeniedException("You do not have permission to access resource: " + resourceId);
    }
}
