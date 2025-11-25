package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqttAuthService {
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoderInternal;

    @Value("${mqtt.bridge.username}")
    private String bridgeUsername;

    @Value("${mqtt.bridge.password}")
    private String bridgePassword;

    @Transactional(readOnly = true)
    public boolean authenticateMqttClient(String username, String password) {
        // 1. Check whether this is a system Bridge client
        if (isBridgeClient(username)) {
            if (isValidBridgePassword(password)) {
                // TODO: In the future, it would be better to store the bridge password hash and use passwordEncoderInternal.matches().
                log.info("MQTT Auth successful for system client: {}", username);
                return true;
            } else {
                log.warn("MQTT Auth failed: Invalid credentials for system client: {}", username);
                return false;
            }
        }

        // 2. If it is not Bridge, check it as a regular device
        return authenticateDevice(username, password);
    }

    private boolean isBridgeClient(String username) {
        return bridgeUsername != null && bridgeUsername.equals(username);
    }

    private boolean isValidBridgePassword(String password) {
        return bridgePassword != null && bridgePassword.equals(password);
    }

    private boolean authenticateDevice(String deviceId, String password) {
        Optional<Device> deviceOptional = deviceRepository.findByDeviceIdAndIsActiveTrue(deviceId);

        if (deviceOptional.isPresent()) {
            Device device = deviceOptional.get();
            if (passwordEncoderInternal.matches(password, device.getHashedDeviceToken())) {
                log.info("MQTT Auth successful for deviceId: {}", deviceId);
                return true;
            } else {
                log.warn("MQTT Auth failed (Invalid Token) for deviceId: {}", deviceId);
            }
        } else {
            log.warn("MQTT Auth failed (Device ID Not Found or Inactive): {}", deviceId);
        }
        return false;
    }
}
