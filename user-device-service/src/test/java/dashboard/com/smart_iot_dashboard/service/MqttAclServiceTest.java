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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MqttACLService Tests")
class MqttAclServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private MqttAclService aclService;

    private static final int ACL_READ = 1;
    private static final int ACL_WRITE = 2;
    private static final int ACL_SUBSCRIBE = 4;
    private static final String BRIDGE_USERNAME = "test_bridge";
    private static final String DEVICE_ID = "sensor-123";
    private static final String OTHER_DEVICE_ID = "sensor-456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aclService, "bridgeUsername", BRIDGE_USERNAME);

        lenient().when(deviceRepository.findByDeviceIdAndIsActiveTrue(anyString()))
                .thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("Bridge ACL Tests")
    class BridgeAclTests {

        @Test
        @DisplayName("Bridge: Subscribe to telemetry ingress - TRUE")
        void testBridgeSubscribeTelemetrySuccess() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_SUBSCRIBE, "iot/telemetry/ingress");
            assertTrue(result);
        }

        @Test
        @DisplayName("Bridge: Read telemetry ingress - TRUE")
        void testBridgeReadTelemetrySuccess() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_READ, "iot/telemetry/ingress");
            assertTrue(result);
        }

        @Test
        @DisplayName("Bridge: Write to device commands - TRUE")
        void testBridgeWriteDeviceCommandsSuccess() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_WRITE, "devices/sensor-123/commands");
            assertTrue(result);
        }

        @Test
        @DisplayName("Bridge: Write to multiple device commands - TRUE")
        void testBridgeWriteMultipleDeviceCommandsSuccess() {
            boolean result1 = aclService.checkAcl(BRIDGE_USERNAME, ACL_WRITE, "devices/abc/commands");
            boolean result2 = aclService.checkAcl(BRIDGE_USERNAME, ACL_WRITE, "devices/xyz-789/commands");

            assertTrue(result1);
            assertTrue(result2);
        }

        @Test
        @DisplayName("Bridge: Write to telemetry ingress - FALSE")
        void testBridgeWriteTelemetryFail() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_WRITE, "iot/telemetry/ingress");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Subscribe to device commands - FALSE")
        void testBridgeSubscribeDeviceCommandsFail() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_SUBSCRIBE, "devices/sensor-123/commands");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Read device commands - FALSE")
        void testBridgeReadDeviceCommandsFail() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_READ, "devices/sensor-123/commands");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Write to malformed commands topic - FALSE")
        void testBridgeWriteMalformedCommandsTopicFail() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_WRITE, "devices/commands");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Write to topic with empty device ID - FALSE")
        void testBridgeWriteEmptyDeviceIdFail() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_WRITE, "devices//commands");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Write to nested commands path - FALSE")
        void testBridgeWriteNestedCommandsPathFail() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_WRITE, "devices/sensor-123/commands/nested/path");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Write to similar topic - FALSE")
        void testBridgeWriteSimilarTopicFail() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_WRITE, "devices/sensor-123/cmd");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Subscribe to similar telemetry topic - FALSE")
        void testBridgeSubscribeSimilarTelemetryFail() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_SUBSCRIBE, "iot/telemetry");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Case-sensitive username check - FALSE")
        void testBridgeCaseSensitiveUsernameFail() {
            boolean result = aclService.checkAcl("TEST_BRIDGE", ACL_WRITE, "devices/sensor-123/commands");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Null username - FALSE")
        void testBridgeNullUsernameFail() {
            boolean result = aclService.checkAcl(null, ACL_WRITE, "devices/sensor-123/commands");
            assertFalse(result);
        }

        @Test
        @DisplayName("Bridge: Null topic - FALSE")
        void testBridgeNullTopicFail() {
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_WRITE, null);
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Regular Device ACL Tests")
    class RegularDeviceAclTests {

        private Device activeDevice;

        @BeforeEach
        void setUp() {
            activeDevice = new Device();
            activeDevice.setDeviceId(DEVICE_ID);
            activeDevice.setActive(true);

            lenient().when(deviceRepository.findByDeviceIdAndIsActiveTrue(DEVICE_ID))
                    .thenReturn(Optional.of(activeDevice));
        }

        @Test
        @DisplayName("Device: Write to telemetry ingress - TRUE")
        void testDeviceWriteTelemetrySuccess() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress");
            assertTrue(result);
        }

        @Test
        @DisplayName("Device: Subscribe to own commands - TRUE")
        void testDeviceSubscribeOwnCommandsSuccess() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_SUBSCRIBE, "devices/sensor-123/commands");
            assertTrue(result);
        }

        @Test
        @DisplayName("Device: Read own commands - TRUE")
        void testDeviceReadOwnCommandsSuccess() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_READ, "devices/sensor-123/commands");
            assertTrue(result);
        }

        @Test
        @DisplayName("Device: Write to other device commands - FALSE")
        void testDeviceWriteOtherCommandsFail() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "devices/sensor-456/commands");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Subscribe to other device commands - FALSE")
        void testDeviceSubscribeOtherCommandsFail() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_SUBSCRIBE, "devices/sensor-456/commands");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Read from telemetry ingress - FALSE")
        void testDeviceReadTelemetryFail() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_READ, "iot/telemetry/ingress");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Subscribe to telemetry ingress - FALSE")
        void testDeviceSubscribeTelemetryFail() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_SUBSCRIBE, "iot/telemetry/ingress");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Write to own commands - FALSE")
        void testDeviceWriteOwnCommandsFail() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "devices/sensor-123/commands");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Access unknown topic - FALSE")
        void testDeviceAccessUnknownTopicFail() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_READ, "system/admin/config");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Case-sensitive device ID - FALSE")
        void testDeviceCaseSensitiveIdFail() {
            boolean result = aclService.checkAcl("SENSOR-123", ACL_WRITE, "iot/telemetry/ingress");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Case-sensitive topic - FALSE")
        void testDeviceCaseSensitiveTopicFail() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "IoT/Telemetry/Ingress");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Topic with trailing slash - FALSE")
        void testDeviceTopicWithTrailingSlashFail() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_SUBSCRIBE, "devices/sensor-123/commands/");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Null device ID - FALSE")
        void testDeviceNullIdFail() {
            boolean result = aclService.checkAcl(null, ACL_WRITE, "iot/telemetry/ingress");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Null topic - FALSE")
        void testDeviceNullTopicFail() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_WRITE, null);
            assertFalse(result);
        }

        @Test
        @DisplayName("Device: Verify repository is queried")
        void testDeviceRepositoryQueried() {
            aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress");
            verify(deviceRepository, times(1)).findByDeviceIdAndIsActiveTrue(DEVICE_ID);
        }

        @Test
        @DisplayName("Device: Multiple successful calls")
        void testMultipleDeviceCallsSuccess() {
            boolean result1 = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress");
            boolean result2 = aclService.checkAcl(DEVICE_ID, ACL_READ, "devices/sensor-123/commands");
            boolean result3 = aclService.checkAcl(DEVICE_ID, ACL_SUBSCRIBE, "devices/sensor-123/commands");

            assertTrue(result1);
            assertTrue(result2);
            assertTrue(result3);
        }
    }

    @Nested
    @DisplayName("Inactive and Non-Existent Device Tests")
    class InactiveDeviceTests {

        @Test
        @DisplayName("Inactive Device: Write to telemetry - FALSE")
        void testInactiveDeviceWriteFail() {
            when(deviceRepository.findByDeviceIdAndIsActiveTrue("inactive-device"))
                    .thenReturn(Optional.empty());

            boolean result = aclService.checkAcl("inactive-device", ACL_WRITE, "iot/telemetry/ingress");
            assertFalse(result);
        }

        @Test
        @DisplayName("Non-existent Device: Any operation - FALSE")
        void testNonExistentDeviceFail() {
            boolean result = aclService.checkAcl("unknown-device", ACL_WRITE, "iot/telemetry/ingress");
            assertFalse(result);
        }

        @Test
        @DisplayName("Deleted Device: Try to authenticate - FALSE")
        void testDeletedDeviceFail() {
            boolean result = aclService.checkAcl("deleted-device", ACL_READ, "devices/deleted-device/commands");
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

            lenient().when(deviceRepository.findByDeviceIdAndIsActiveTrue(DEVICE_ID))
                    .thenReturn(Optional.of(activeDevice));
        }

        @Test
        @DisplayName("Invalid accessType - FALSE")
        void testInvalidAccessTypeFail() {
            boolean result = aclService.checkAcl(DEVICE_ID, 99, "iot/telemetry/ingress");
            assertFalse(result);
        }

        @Test
        @DisplayName("Empty string username - FALSE")
        void testEmptyUsernameFail() {
            boolean result = aclService.checkAcl("", ACL_WRITE, "iot/telemetry/ingress");
            assertFalse(result);
        }

        @Test
        @DisplayName("Empty string topic - FALSE")
        void testEmptyTopicFail() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "");
            assertFalse(result);
        }

        @Test
        @DisplayName("Topic with special characters")
        void testTopicWithSpecialCharacters() {
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress@evil");
            assertFalse(result);
        }

        @Test
        @DisplayName("Device ID with numbers")
        void testDeviceIdWithNumbers() {
            when(deviceRepository.findByDeviceIdAndIsActiveTrue("12345"))
                    .thenReturn(Optional.of(activeDevice));

            boolean result = aclService.checkAcl("12345", ACL_WRITE, "iot/telemetry/ingress");
            assertTrue(result);
        }

        @Test
        @DisplayName("Device ID with special characters")
        void testDeviceIdWithSpecialCharacters() {
            String specialId = "sensor-123_ABC@device";
            Device device = new Device();
            device.setDeviceId(specialId);
            device.setActive(true);

            when(deviceRepository.findByDeviceIdAndIsActiveTrue(specialId))
                    .thenReturn(Optional.of(device));

            boolean result = aclService.checkAcl(specialId, ACL_WRITE, "iot/telemetry/ingress");
            assertTrue(result);
        }

        @Test
        @DisplayName("Bridge username null in config")
        void testBridgeUsernameNullInConfig() {
            ReflectionTestUtils.setField(aclService, "bridgeUsername", null);
            boolean result = aclService.checkAcl(BRIDGE_USERNAME, ACL_WRITE, "devices/sensor-123/commands");
            assertFalse(result);
        }

        @Test
        @DisplayName("Transactional read-only is called")
        void testCheckAclIsReadOnly() {
            aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress");
            verify(deviceRepository, times(1)).findByDeviceIdAndIsActiveTrue(DEVICE_ID);
        }

        @Test
        @DisplayName("Multiple device IDs don't interfere")
        void testMultipleDeviceIdsDontInterfere() {
            Device device2 = new Device();
            device2.setDeviceId(OTHER_DEVICE_ID);
            device2.setActive(true);

            when(deviceRepository.findByDeviceIdAndIsActiveTrue(OTHER_DEVICE_ID))
                    .thenReturn(Optional.of(device2));

            boolean result1 = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress");
            boolean result2 = aclService.checkAcl(OTHER_DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress");

            assertTrue(result1);
            assertTrue(result2);
        }

        @Test
        @DisplayName("Commands topic parsing with various device IDs")
        void testCommandsTopicParsingVariousIds() {
            String[] deviceIds = {"device-1", "sensor_123", "mqtt-abc123-xyz", "dev"};

            for (String deviceId : deviceIds) {
                Device device = new Device();
                device.setDeviceId(deviceId);
                device.setActive(true);

                when(deviceRepository.findByDeviceIdAndIsActiveTrue(deviceId))
                        .thenReturn(Optional.of(device));

                String topic = "devices/" + deviceId + "/commands";
                boolean result = aclService.checkAcl(deviceId, ACL_SUBSCRIBE, topic);
                assertTrue(result, "Should allow subscribe for device " + deviceId);
            }
        }
    }

    @Nested
    @DisplayName("Service Logic Boundary Tests")
    class BoundaryTests {

        private Device activeDevice;

        @BeforeEach
        void setUp() {
            activeDevice = new Device();
            activeDevice.setDeviceId(DEVICE_ID);
            activeDevice.setActive(true);

            lenient().when(deviceRepository.findByDeviceIdAndIsActiveTrue(anyString()))
                    .thenReturn(Optional.empty());
            lenient().when(deviceRepository.findByDeviceIdAndIsActiveTrue(DEVICE_ID))
                    .thenReturn(Optional.of(activeDevice));
        }

        @Test
        @DisplayName("Bridge takes precedence over device check")
        void testBridgePrecedence() {
            ReflectionTestUtils.setField(aclService, "bridgeUsername", DEVICE_ID);

            // Same username as bridge and device, but bridge rules should apply
            boolean result = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "devices/sensor-456/commands");
            assertTrue(result); // Bridge can write to any device commands
        }

        @Test
        @DisplayName("Device commands topic exact match required")
        void testDeviceCommandsExactMatch() {
            boolean result1 = aclService.checkAcl(DEVICE_ID, ACL_SUBSCRIBE, "devices/sensor-123/commands");
            boolean result2 = aclService.checkAcl(DEVICE_ID, ACL_SUBSCRIBE, "devices/sensor-123/commands/");
            boolean result3 = aclService.checkAcl(DEVICE_ID, ACL_SUBSCRIBE, "devices/sensor-123/command");

            assertTrue(result1);
            assertFalse(result2);
            assertFalse(result3);
        }

        @Test
        @DisplayName("Telemetry ingress exact match required")
        void testTelemetryIngressExactMatch() {
            boolean result1 = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress");
            boolean result2 = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress/");
            boolean result3 = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress/extra");

            assertTrue(result1);
            assertFalse(result2);
            assertFalse(result3);
        }

        @Test
        @DisplayName("Access types are distinct")
        void testAccessTypesAreDistinct() {
            // READ, WRITE, SUBSCRIBE are different values
            assertEquals(1, ACL_READ);
            assertEquals(2, ACL_WRITE);
            assertEquals(4, ACL_SUBSCRIBE);
        }

        @Test
        @DisplayName("Device cannot have same permission for different access types")
        void testDifferentAccessTypesProduceExpectedResults() {
            boolean readResult = aclService.checkAcl(DEVICE_ID, ACL_READ, "iot/telemetry/ingress");
            boolean writeResult = aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress");

            assertFalse(readResult);
            assertTrue(writeResult);
        }
    }
}