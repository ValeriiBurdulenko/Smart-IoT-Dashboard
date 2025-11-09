package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.dto.KeycloakEvent;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KeycloakWebhookServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private KeycloakWebhookService keycloakWebhookService;


    @Captor
    private ArgumentCaptor<Instant> instantCaptor;

    @Test
    void testProcessEvent_DeleteAccount_Success() {
        String testUserId = "user-to-delete-123";
        KeycloakEvent deleteEvent = new KeycloakEvent();
        deleteEvent.setType("DELETE_ACCOUNT");
        deleteEvent.setUserId(testUserId);

        // Act
        keycloakWebhookService.processEvent(deleteEvent);

        // Assert
        verify(deviceRepository).deactivateDevicesByUserId(eq(testUserId), any(Instant.class));

        // (Optional) More stringent verification:
        // verify(deviceRepository).deactivateDevicesByUserId(eq(testUserId), instantCaptor.capture());
        // assertThat(instantCaptor.getValue()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void testProcessEvent_RegisterEvent_DoesNothing() {
        // Arrange
        KeycloakEvent registerEvent = new KeycloakEvent();
        registerEvent.setType("REGISTER");
        registerEvent.setUserId("new-user-456");

        // Act
        keycloakWebhookService.processEvent(registerEvent);

        // Assert
        verify(deviceRepository, never()).deactivateDevicesByUserId(any(), any());
    }

    @Test
    void testProcessEvent_DeleteEvent_NoUserId_DoesNothing() {
        // Arrange
        KeycloakEvent deleteEventNoUser = new KeycloakEvent();
        deleteEventNoUser.setType("DELETE_ACCOUNT");
        deleteEventNoUser.setUserId(null);

        // Act
        keycloakWebhookService.processEvent(deleteEventNoUser);

        // Assert
        verify(deviceRepository, never()).deactivateDevicesByUserId(any(), any());
    }
}
