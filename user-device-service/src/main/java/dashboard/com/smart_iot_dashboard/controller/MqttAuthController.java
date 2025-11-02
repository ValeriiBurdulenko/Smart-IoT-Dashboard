package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.MqttAuthRequest;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/internal/mqtt")
@RequiredArgsConstructor
@Slf4j
public class MqttAuthController {

    private final DeviceRepository deviceRepository;
    private final PasswordEncoder deviceTokenEncoder;

    @Value("${mqtt.bridge.username}")
    private String bridgeUsername;

    @Value("${mqtt.bridge.password}")
    private String bridgePassword;


    @PostMapping("/auth")
    public ResponseEntity<Void> authenticateMqttClient(@RequestBody MqttAuthRequest authRequest) {
        String username = authRequest.getUsername();
        String password = authRequest.getPassword();
        log.info("MQTT Auth attempt for deviceId: {}", username);

        // 1. Check whether this is a system Bridge client
        if (bridgeUsername != null && bridgeUsername.equals(username)) {
            if (bridgePassword != null && bridgePassword.equals(password)) {
                log.info("MQTT Auth successful for system client: {}", username);
                return ResponseEntity.ok().build();
            } else {
                log.warn("MQTT Auth failed (Invalid Password) for system client: {}", username);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        // 2. If it is not Bridge, check it as a regular device
        Optional<Device> deviceOptional = deviceRepository.findByDeviceId(username);

        if (deviceOptional.isPresent()) {
            Device device = deviceOptional.get();
            if (deviceTokenEncoder.matches(password, device.getHashedDeviceToken())) {
                log.info("MQTT Auth successful for deviceId: {}", username);
                return ResponseEntity.ok().build();
            } else {
                log.warn("MQTT Auth failed (Invalid Token) for deviceId: {}", username);
            }
        } else {
            log.warn("MQTT Auth failed (Device ID Not Found): {}", username);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
