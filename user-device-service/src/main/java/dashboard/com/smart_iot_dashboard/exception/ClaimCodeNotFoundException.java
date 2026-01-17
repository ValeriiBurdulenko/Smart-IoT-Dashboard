package dashboard.com.smart_iot_dashboard.exception;

public class ClaimCodeNotFoundException extends ResourceNotFoundException {

    public ClaimCodeNotFoundException(String claimCode) {
        super("Claim code not found or expired: " + claimCode);
    }
}
