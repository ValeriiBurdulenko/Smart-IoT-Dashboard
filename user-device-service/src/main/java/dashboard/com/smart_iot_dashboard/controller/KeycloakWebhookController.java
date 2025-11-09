package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.KeycloakEvent;
import dashboard.com.smart_iot_dashboard.service.KeycloakWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/keycloak-events")
@RequiredArgsConstructor
@Slf4j
public class KeycloakWebhookController {

    private final KeycloakWebhookService webhookService;

    @Value("${keycloak.webhook.secret}")
    private String expectedApiKey;

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    @PostMapping
    public ResponseEntity<Void> handleKeycloakEvent(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @RequestBody KeycloakEvent event) {

        // 1. SECURITY CHECK: Checking our secret key
        if (apiKey == null || !apiKey.equals(expectedApiKey)) {
            log.warn("Unauthorized Keycloak webhook attempt. Invalid API Key.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. PROCESSING: (Можно сделать асинхронно @Async, чтобы Keycloak не ждал)
        try {
            webhookService.processEvent(event);
            return ResponseEntity.ok().build(); // Always return 200 OK so Keycloak does not retry
        } catch (Exception e) {
            log.error("Error processing Keycloak event: {}", e.getMessage());
            // Even in case of an error, we return 200 so that Keycloak does not spam
            // (or 500 if we want it to try again)
            return ResponseEntity.status(HttpStatus.OK).build();
        }
    }
}
