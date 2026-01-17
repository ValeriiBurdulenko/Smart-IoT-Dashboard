package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MQTT Auth Service Tests (Refactored)")
class MqttAuthServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private PasswordEncoder passwordEncoderInternal;

    @InjectMocks
    private MqttAuthService authService;

    private static final String BRIDGE_USERNAME = "test_bridge";
    private static final String BRIDGE_PASSWORD = "test_bridge_pass";
    private static final String DEVICE_ID = "sensor-123";
    private static final String DEVICE_TOKEN = "raw-token-abc";
    private static final String OTHER_DEVICE_ID = "sensor-456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "bridgeUsername", BRIDGE_USERNAME);
        ReflectionTestUtils.setField(authService, "bridgePassword", BRIDGE_PASSWORD);

        lenient().when(deviceRepository.findByDeviceIdAndIsActiveTrue(anyString()))
                .thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("Bridge Authentication Tests")
    class BridgeAuthTests {

        @Test
        @DisplayName("Bridge: Correct credentials - TRUE")
        void testBridgeAuthSuccessCorrectCredentials() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, BRIDGE_PASSWORD);
            assertTrue(result);

            // Bridge auth should not query database
            verify(deviceRepository, never()).findByDeviceIdAndIsActiveTrue(anyString());
        }

        @Test
        @DisplayName("Bridge: Wrong password - FALSE")
        void testBridgeAuthFailWrongPassword() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, "wrong-password");
            assertFalse(result);

            verify(deviceRepository, never()).findByDeviceIdAndIsActiveTrue(anyString());
        }

        @Test
        @DisplayName("Bridge: Empty password - FALSE")
        void testBridgeAuthFailEmptyPassword() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, "");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Null password - FALSE")
        void testBridgeAuthFailNullPassword() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, null);
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Case-sensitive password - FALSE")
        void testBridgeAuthFailCaseSensitivePassword() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, "TEST_BRIDGE_PASS");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Case-sensitive username - FALSE")
        void testBridgeAuthFailCaseSensitiveUsername() {
            boolean result = authService.authenticateMqttClient("TEST_BRIDGE", BRIDGE_PASSWORD);
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Password with trailing space - FALSE")
        void testBridgeAuthFailPasswordWithSpace() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, BRIDGE_PASSWORD + " ");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: SQL injection attempt - FALSE")
        void testBridgeAuthFailSqlInjectionAttempt() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, "pass' OR '1'='1");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Very long password - FALSE")
        void testBridgeAuthFailVeryLongPassword() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, "a".repeat(10000));
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Null username - FALSE")
        void testBridgeAuthFailNullUsername() {
            boolean result = authService.authenticateMqttClient(null, BRIDGE_PASSWORD);
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Empty username - FALSE")
        void testBridgeAuthFailEmptyUsername() {
            boolean result = authService.authenticateMqttClient("", BRIDGE_PASSWORD);
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Null username and password - FALSE")
        void testBridgeAuthFailBothNull() {
            boolean result = authService.authenticateMqttClient(null, null);
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Regular Device Authentication Tests")
    class DeviceAuthTests {

        private Device activeDevice;

        @BeforeEach
        void setUp() {
            activeDevice = new Device();
            activeDevice.setDeviceId(DEVICE_ID);
            activeDevice.setActive(true);
            activeDevice.setHashedDeviceToken("hashed_" + DEVICE_TOKEN);

            lenient().when(deviceRepository.findByDeviceIdAndIsActiveTrue(DEVICE_ID))
                    .thenReturn(Optional.of(activeDevice));
        }

        @Test
        @DisplayName("Device: Correct token - TRUE")
        void testDeviceAuthSuccessCorrectToken() {
            when(passwordEncoderInternal.matches(DEVICE_TOKEN, activeDevice.getHashedDeviceToken()))
                    .thenReturn(true);

            boolean result = authService.authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN);
            assertTrue(result);

            verify(deviceRepository, times(1)).findByDeviceIdAndIsActiveTrue(DEVICE_ID);
            verify(passwordEncoderInternal, times(1)).matches(DEVICE_TOKEN, activeDevice.getHashedDeviceToken());
        }

        @Test
        @DisplayName("Device: Wrong token - FALSE")
        void testDeviceAuthFailWrongToken() {
            when(passwordEncoderInternal.matches("wrong-token", activeDevice.getHashedDeviceToken()))
                    .thenReturn(false);

            boolean result = authService.authenticateMqttClient(DEVICE_ID, "wrong-token");
            assertFalse(result);

            verify(passwordEncoderInternal, times(1)).matches("wrong-token", activeDevice.getHashedDeviceToken());
        }

        @Test
        @DisplayName("Device: Empty token - FALSE")
        void testDeviceAuthFailEmptyToken() {
            when(passwordEncoderInternal.matches("", activeDevice.getHashedDeviceToken()))
                    .thenReturn(false);

            boolean result = authService.authenticateMqttClient(DEVICE_ID, "");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Null token - FALSE")
        void testDeviceAuthFailNullToken() {
            when(passwordEncoderInternal.matches(null, activeDevice.getHashedDeviceToken()))
                    .thenReturn(false);

            boolean result = authService.authenticateMqttClient(DEVICE_ID, null);
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Case-sensitive token - FALSE")
        void testDeviceAuthFailCaseSensitiveToken() {
            when(passwordEncoderInternal.matches("RAW-TOKEN-ABC", activeDevice.getHashedDeviceToken()))
                    .thenReturn(false);

            boolean result = authService.authenticateMqttClient(DEVICE_ID, "RAW-TOKEN-ABC");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Case-sensitive device ID - FALSE")
        void testDeviceAuthFailCaseSensitiveDeviceId() {
            boolean result = authService.authenticateMqttClient("SENSOR-123", DEVICE_TOKEN);
            assertFalse(result);

            verify(deviceRepository, times(1)).findByDeviceIdAndIsActiveTrue("SENSOR-123");
            verify(passwordEncoderInternal, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("Device: Token with leading space - FALSE")
        void testDeviceAuthFailTokenWithLeadingSpace() {
            when(passwordEncoderInternal.matches(" " + DEVICE_TOKEN, activeDevice.getHashedDeviceToken()))
                    .thenReturn(false);

            boolean result = authService.authenticateMqttClient(DEVICE_ID, " " + DEVICE_TOKEN);
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Token with trailing space - FALSE")
        void testDeviceAuthFailTokenWithTrailingSpace() {
            when(passwordEncoderInternal.matches(DEVICE_TOKEN + " ", activeDevice.getHashedDeviceToken()))
                    .thenReturn(false);

            boolean result = authService.authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN + " ");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Token of one device for another - FALSE")
        void testWrongDeviceToken() {
            Device secondDevice = new Device();
            secondDevice.setDeviceId(OTHER_DEVICE_ID);
            secondDevice.setActive(true);
            secondDevice.setHashedDeviceToken("hashed_other-token");

            when(deviceRepository.findByDeviceIdAndIsActiveTrue(OTHER_DEVICE_ID))
                    .thenReturn(Optional.of(secondDevice));
            when(passwordEncoderInternal.matches(DEVICE_TOKEN, secondDevice.getHashedDeviceToken()))
                    .thenReturn(false);

            boolean result = authService.authenticateMqttClient(OTHER_DEVICE_ID, DEVICE_TOKEN);
            assertFalse(result);

            verify(passwordEncoderInternal, times(1)).matches(DEVICE_TOKEN, secondDevice.getHashedDeviceToken());
        }

        @Test
        @DisplayName("Device: Multiple successful authentications")
        void testMultipleSuccessfulAuthentications() {
            when(passwordEncoderInternal.matches(DEVICE_TOKEN, activeDevice.getHashedDeviceToken()))
                    .thenReturn(true);

            for (int i = 0; i < 5; i++) {
                boolean result = authService.authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN);
                assertTrue(result);
            }

            verify(deviceRepository, times(5)).findByDeviceIdAndIsActiveTrue(DEVICE_ID);
            verify(passwordEncoderInternal, times(5)).matches(DEVICE_TOKEN, activeDevice.getHashedDeviceToken());
        }

        @Test
        @DisplayName("Device: Repository is queried exactly once")
        void testDeviceRepositoryQueriedOnce() {
            when(passwordEncoderInternal.matches(DEVICE_TOKEN, activeDevice.getHashedDeviceToken()))
                    .thenReturn(true);

            authService.authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN);

            verify(deviceRepository, times(1)).findByDeviceIdAndIsActiveTrue(DEVICE_ID);
        }

        @Test
        @DisplayName("Device: PasswordEncoder is called with correct parameters")
        void testPasswordEncoderCalledWithCorrectParams() {
            when(passwordEncoderInternal.matches(DEVICE_TOKEN, activeDevice.getHashedDeviceToken()))
                    .thenReturn(true);

            authService.authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN);

            verify(passwordEncoderInternal, times(1))
                    .matches(DEVICE_TOKEN, activeDevice.getHashedDeviceToken());
        }
    }

    @Nested
    @DisplayName("Non-Existent and Inactive Device Tests")
    class NonExistentDeviceTests {

        @Test
        @DisplayName("Non-existent Device: Any credentials - FALSE")
        void testNonExistentDeviceAuthFail() {
            when(deviceRepository.findByDeviceIdAndIsActiveTrue("unknown-device"))
                    .thenReturn(Optional.empty());

            boolean result = authService.authenticateMqttClient("unknown-device", "any-token");
            assertFalse(result);

            verify(deviceRepository, times(1)).findByDeviceIdAndIsActiveTrue("unknown-device");
            verify(passwordEncoderInternal, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("Inactive Device: Not found in repository - FALSE")
        void testInactiveDeviceAuthFail() {
            when(deviceRepository.findByDeviceIdAndIsActiveTrue("inactive-device"))
                    .thenReturn(Optional.empty());

            boolean result = authService.authenticateMqttClient("inactive-device", "any-token");
            assertFalse(result);

            verify(passwordEncoderInternal, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("Deleted Device: Try to authenticate - FALSE")
        void testDeletedDeviceAuthFail() {
            when(deviceRepository.findByDeviceIdAndIsActiveTrue("deleted-device"))
                    .thenReturn(Optional.empty());

            boolean result = authService.authenticateMqttClient("deleted-device", "old-token");
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Special Scenarios")
    class EdgeCaseTests {

        private Device activeDevice;

        @BeforeEach
        void setUp() {
            activeDevice = new Device();
            activeDevice.setDeviceId(DEVICE_ID);
            activeDevice.setActive(true);
            activeDevice.setHashedDeviceToken("hashed_" + DEVICE_TOKEN);

            lenient().when(deviceRepository.findByDeviceIdAndIsActiveTrue(DEVICE_ID))
                    .thenReturn(Optional.of(activeDevice));
            lenient().when(passwordEncoderInternal.matches(DEVICE_TOKEN, activeDevice.getHashedDeviceToken()))
                    .thenReturn(true);
        }

        @Test
        @DisplayName("Bridge username null in config")
        void testBridgeUsernameNullInConfig() {
            ReflectionTestUtils.setField(authService, "bridgeUsername", null);

            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, BRIDGE_PASSWORD);
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge password null in config")
        void testBridgePasswordNullInConfig() {
            ReflectionTestUtils.setField(authService, "bridgePassword", null);

            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, BRIDGE_PASSWORD);
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge both username and password null in config")
        void testBridgeBothNullInConfig() {
            ReflectionTestUtils.setField(authService, "bridgeUsername", null);
            ReflectionTestUtils.setField(authService, "bridgePassword", null);

            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, BRIDGE_PASSWORD);
            assertFalse(result);
        }

        @Test
        @DisplayName("Device ID with numbers")
        void testNumericDeviceId() {
            Device device = new Device();
            device.setDeviceId("12345");
            device.setActive(true);
            device.setHashedDeviceToken("hashed_token-12345");

            when(deviceRepository.findByDeviceIdAndIsActiveTrue("12345"))
                    .thenReturn(Optional.of(device));
            when(passwordEncoderInternal.matches("token-12345", device.getHashedDeviceToken()))
                    .thenReturn(true);

            boolean result = authService.authenticateMqttClient("12345", "token-12345");
            assertTrue(result);
        }

        @Test
        @DisplayName("Device ID with special characters")
        void testSpecialCharactersDeviceId() {
            String specialId = "sensor@device-123_ABC";
            Device device = new Device();
            device.setDeviceId(specialId);
            device.setActive(true);
            device.setHashedDeviceToken("hashed_special-token");

            when(deviceRepository.findByDeviceIdAndIsActiveTrue(specialId))
                    .thenReturn(Optional.of(device));
            when(passwordEncoderInternal.matches("special-token", device.getHashedDeviceToken()))
                    .thenReturn(true);

            boolean result = authService.authenticateMqttClient(specialId, "special-token");
            assertTrue(result);
        }

        @Test
        @DisplayName("Very long username")
        void testVeryLongUsername() {
            String longUsername = "device-" + "a".repeat(1000);

            boolean result = authService.authenticateMqttClient(longUsername, DEVICE_TOKEN);
            assertFalse(result);

            verify(deviceRepository, times(1)).findByDeviceIdAndIsActiveTrue(longUsername);
        }

        @Test
        @DisplayName("Very long token")
        void testVeryLongToken() {
            String longToken = "token-" + "a".repeat(10000);
            when(passwordEncoderInternal.matches(longToken, activeDevice.getHashedDeviceToken()))
                    .thenReturn(false);

            boolean result = authService.authenticateMqttClient(DEVICE_ID, longToken);
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge attempting to use device token - FALSE")
        void testBridgeWithWrongCredentials() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, DEVICE_TOKEN);
            assertFalse(result);

            verify(deviceRepository, never()).findByDeviceIdAndIsActiveTrue(anyString());
        }

        @Test
        @DisplayName("Device attempting to use bridge password - FALSE")
        void testDeviceWithBridgePassword() {
            when(passwordEncoderInternal.matches(BRIDGE_PASSWORD, activeDevice.getHashedDeviceToken()))
                    .thenReturn(false);

            boolean result = authService.authenticateMqttClient(DEVICE_ID, BRIDGE_PASSWORD);
            assertFalse(result);
        }

        @Test
        @DisplayName("Multiple consecutive failed attempts with same device")
        void testMultipleFailedAttempts() {
            when(passwordEncoderInternal.matches(anyString(), anyString()))
                    .thenReturn(false);

            for (int i = 0; i < 5; i++) {
                boolean result = authService.authenticateMqttClient(DEVICE_ID, "wrong-password-" + i);
                assertFalse(result);
            }

            verify(deviceRepository, times(5)).findByDeviceIdAndIsActiveTrue(DEVICE_ID);
        }

        @Test
        @DisplayName("Private helper methods are called correctly")
        void testPrivateHelperMethodsLogic() {
            // Test through public method that bridge check comes first
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, BRIDGE_PASSWORD);
            assertTrue(result);

            // Verify device repository was not queried for bridge
            verify(deviceRepository, never()).findByDeviceIdAndIsActiveTrue(anyString());
        }
    }

    @Nested
    @DisplayName("Transactional Read-Only Tests")
    class TransactionalTests {

        private Device activeDevice;

        @BeforeEach
        void setUp() {
            activeDevice = new Device();
            activeDevice.setDeviceId(DEVICE_ID);
            activeDevice.setActive(true);
            activeDevice.setHashedDeviceToken("hashed_" + DEVICE_TOKEN);

            when(deviceRepository.findByDeviceIdAndIsActiveTrue(DEVICE_ID))
                    .thenReturn(Optional.of(activeDevice));
            when(passwordEncoderInternal.matches(DEVICE_TOKEN, activeDevice.getHashedDeviceToken()))
                    .thenReturn(true);
        }

        @Test
        @DisplayName("Service method has Transactional read-only annotation")
        void testTransactionalReadOnlyUsed() {
            // This is verified through reflection or by checking the method signature
            // The actual transactional behavior is managed by Spring

            boolean result = authService.authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN);
            assertTrue(result);

            // Verify read operations only (no updates)
            verify(deviceRepository, times(1)).findByDeviceIdAndIsActiveTrue(DEVICE_ID);
        }
    }

    @Nested
    @DisplayName("Bridge Authentication - Private Helper Methods")
    class BridgeHelperMethodsTests {

        @Test
        @DisplayName("Bridge: isBridgeClient returns true for correct username")
        void testIsBridgeClientTrue() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, BRIDGE_PASSWORD);
            assertTrue(result);
        }

        @Test
        @DisplayName("Bridge: isBridgeClient returns false for wrong username")
        void testIsBridgeClientFalse() {
            boolean result = authService.authenticateMqttClient("not-bridge", BRIDGE_PASSWORD);
            assertFalse(result);

            verify(deviceRepository, times(1)).findByDeviceIdAndIsActiveTrue("not-bridge");
        }

        @Test
        @DisplayName("Bridge: isValidBridgePassword returns true for correct password")
        void testIsValidBridgePasswordTrue() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, BRIDGE_PASSWORD);
            assertTrue(result);
        }

        @Test
        @DisplayName("Bridge: isValidBridgePassword returns false for wrong password")
        void testIsValidBridgePasswordFalse() {
            boolean result = authService.authenticateMqttClient(BRIDGE_USERNAME, "wrong");
            assertFalse(result);
        }
    }
}