package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.dto.KeycloakEvent;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakWebhookService {

    private final DeviceRepository deviceRepository;
    // (Optional) private final MqttGateway mqttGateway; // If you want to forcefully terminate sessions

    @Transactional
    public void processEvent(KeycloakEvent event) {
        log.info("Keycloak event received: Type={}, UserId={}", event.getType(), event.getUserId());

        // (ADMIN_DELETE, DELETE_ACCOUNT etc.)
        if (event.getType() != null && event.getType().contains("DELETE")) {
            String userId = event.getUserId();
            if (userId == null || userId.isBlank()) {
                log.warn("Delete event received but UserId is missing.");
                return;
            }

            log.warn("DEACTIVATING all devices for deleted user: {}", userId);

            int deactivatedCount = deviceRepository.deactivateDevicesByUserId(userId, Instant.now());

            log.info("Deactivated {} devices for user {}", deactivatedCount, userId);

            // (Optional) You can also send an MQTT command
            // to forcefully disconnect this user's active sessions,
            // although the ‘go-auth’ cache (5 min) will disconnect them soon anyway.
        }
    }
}
