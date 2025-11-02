package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.dto.ClaimResponse;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.exception.ClaimCodeNotFoundException;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Aktiviert Mockito für JUnit 5
class ProvisioningServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private PasswordEncoder deviceTokenEncoder;

    // Spezieller Mock für Redis' .opsForValue()
    @Mock
    private ValueOperations<String, String> valueOperations;

    // Test-Objekt: Die Klasse, die wir testen.
    // Mockito injiziert die @Mock-Objekte automatisch hier hinein.
    @InjectMocks
    private ProvisioningService provisioningService;

    @BeforeEach
    void setUp() {
        // Wir müssen Mockito sagen, dass redisTemplate.opsForValue()
        // unseren simulierten valueOperations-Mock zurückgeben soll.
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ReflectionTestUtils.setField(
                provisioningService,
                "claimCodeTtlMinutes",
                5L
        );
    }

    @Test
    void test_generateClaimCode_success() {
        // Arrange (Vorbereitung)
        String testUserId = "user-id-123";

        // Wir simulieren, dass der Redis-Schlüssel (der Code) beim ersten Mal "frei" ist
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // Act (Ausführung)
        String claimCode = provisioningService.generateClaimCode(testUserId);

        // Assert (Überprüfung)
        assertThat(claimCode).isNotNull();
        assertThat(claimCode).matches("^\\d{3}-\\d{3}$"); // Prüft das Format XXX-XXX

        // Überprüft, ob redisTemplate.opsForValue().set() korrekt aufgerufen wurde
        verify(valueOperations).set(
                eq("claimcode:" + claimCode), // Der erwartete Schlüssel
                eq(testUserId), // Der erwartete Wert (User ID)
                eq(Duration.ofMinutes(5)) // Die erwartete TTL
        );
    }

    @Test
    void test_claimDevice_success() {
        // Arrange
        String claimCode = "123-456";
        String redisKey = "claimcode:" + claimCode;
        String testUserId = "user-id-abc";
        String dummyHash = "hashed-token-string";

        // 1. Simuliere, dass der Code in Redis gefunden wird
        when(valueOperations.get(redisKey)).thenReturn(testUserId);

        // 2. Simuliere die Antwort des PasswordEncoders
        when(deviceTokenEncoder.encode(anyString())).thenReturn(dummyHash);

        // 3. Simuliere die Antwort des Repositories (gibt das gespeicherte Objekt zurück)
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ClaimResponse response = provisioningService.claimDevice(claimCode);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getDeviceId()).isNotNull().isNotEmpty();
        assertThat(response.getDeviceToken()).isNotNull().isNotEmpty();

        // Überprüfe, dass der zurückgegebene Token NICHT der gehashte ist
        assertThat(response.getDeviceToken()).isNotEqualTo(dummyHash);

        // --- Überprüfe die Interaktionen ---

        // 1. Wurde der Encoder mit dem *rohen* Token aufgerufen?
        verify(deviceTokenEncoder).encode(eq(response.getDeviceToken()));

        // 2. Wurde das korrekte Objekt in der DB gespeichert?
        // Wir fangen das Objekt ab, das an deviceRepository.save() übergeben wurde
        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(deviceCaptor.capture());

        Device savedDevice = deviceCaptor.getValue();
        assertThat(savedDevice.getDeviceId()).isEqualTo(response.getDeviceId());
        assertThat(savedDevice.getUserId()).isEqualTo(testUserId);
        assertThat(savedDevice.getHashedDeviceToken()).isEqualTo(dummyHash); // Der Hash muss gespeichert werden!

        // 3. Wurde der Code aus Redis gelöscht?
        verify(redisTemplate).delete(redisKey);
    }

    @Test
    void test_claimDevice_fail_codeNotFoundOrExpired() {
        // Arrange
        String claimCode = "999-000";
        String redisKey = "claimcode:" + claimCode;

        // Simuliere, dass der Code in Redis NICHT gefunden wird (oder abgelaufen ist)
        when(valueOperations.get(redisKey)).thenReturn(null);

        // Act & Assert
        // Überprüfe, ob die spezifische Exception geworfen wird
        assertThatThrownBy(() -> {
            provisioningService.claimDevice(claimCode);
        })
                .isInstanceOf(ClaimCodeNotFoundException.class)
                .hasMessageContaining(claimCode); // Prüft, ob der Code in der Fehlermeldung erwähnt wird

        // Stelle sicher, dass *keine* Speicherung oder Löschung versucht wurde
        verify(deviceRepository, never()).save(any());
        verify(redisTemplate, never()).delete(anyString());
    }
}
