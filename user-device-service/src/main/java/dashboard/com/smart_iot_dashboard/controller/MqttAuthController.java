package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.MqttAuthRequest;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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


    @PostMapping("/auth")
    public ResponseEntity<Void> authenticateMqttClient(@RequestBody MqttAuthRequest authRequest) {
        log.info("MQTT Auth attempt for deviceId: {}", authRequest.getUsername());

        Optional<Device> deviceOptional = deviceRepository.findByDeviceId(authRequest.getUsername());

        if (deviceOptional.isPresent()) {
            Device device = deviceOptional.get();
            if (deviceTokenEncoder.matches(authRequest.getPassword(), device.getHashedDeviceToken())) {
                log.info("MQTT Auth successful for deviceId: {}", authRequest.getUsername());
                return ResponseEntity.ok().build();
            } else {
                log.warn("MQTT Auth failed (Invalid Token) for deviceId: {}", authRequest.getUsername());
            }
        } else {
            log.warn("MQTT Auth failed (Device ID Not Found): {}", authRequest.getUsername());
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
