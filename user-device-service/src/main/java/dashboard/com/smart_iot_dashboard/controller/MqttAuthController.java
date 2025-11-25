package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.MqttAuthRequest;
import dashboard.com.smart_iot_dashboard.service.MqttAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/mqtt")
@RequiredArgsConstructor
@Slf4j
public class MqttAuthController {

    private final MqttAuthService authService;


    @PostMapping("/auth")
    public ResponseEntity<Void> authenticateMqttClient(@Valid @RequestBody MqttAuthRequest authRequest) {
        String username = authRequest.getUsername();
        String password = authRequest.getPassword();

        log.info("MQTT Auth attempt for username: {}", username);

        if (authService.authenticateMqttClient(username, password)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
