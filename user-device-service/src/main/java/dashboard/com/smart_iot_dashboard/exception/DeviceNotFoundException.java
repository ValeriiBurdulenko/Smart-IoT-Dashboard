package dashboard.com.smart_iot_dashboard.exception;

public class DeviceNotFoundException extends ResourceNotFoundException {
    public DeviceNotFoundException(String deviceId) {
        super("Device not found: " + deviceId, "DEVICE_NOT_FOUND");
    }
}
