package dashboard.com.smart_iot_dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dashboard.com.smart_iot_dashboard.config.SecurityConfig;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import dashboard.com.smart_iot_dashboard.service.MqttGateway;
import dashboard.com.smart_iot_dashboard.service.DeviceService;
import dashboard.com.smart_iot_dashboard.service.ProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DeviceCommandController.class, ProvisioningController.class, MqttAclController.class, MqttAuthController.class})
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://dummy-issuer.com"
})
class DeviceCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --- Mocks für ALLE Abhängigkeiten ---
    @MockitoBean
    private MqttGateway mqttGateway;
    @MockitoBean
    private DeviceService deviceService;
    @MockitoBean
    private ProvisioningService provisioningService;
    @MockitoBean
    private DeviceRepository deviceRepository;
    @MockitoBean
    private StringRedisTemplate redisTemplate;
    @MockitoBean
    private ValueOperations<String, String> valueOperations;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private final String TEST_USER_ID = "user-jwt-subject-id";
    private final String TEST_DEVICE_ID = "device-abc-123";

    @Test
    void sendCommandToDevice_Success_WhenActive() throws Exception {
        // 1. Create a realistic mock device with the ID set
        Device mockDevice = new Device();
        mockDevice.setDeviceId(TEST_DEVICE_ID);
        mockDevice.setUserId(TEST_USER_ID);

        // 2. Simuliere, dass das Repository DIESES Gerät findet
        when(deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue(TEST_DEVICE_ID, TEST_USER_ID))
                .thenReturn(Optional.of(mockDevice));

        Map<String, Object> commandPayload = Map.of("command", "set_temp", "value", 25);
        String expectedPayloadJson = objectMapper.writeValueAsString(commandPayload);

        // --- ACT & ASSERT ---
        mockMvc.perform(post("/api/v1/devices/{deviceId}/command", TEST_DEVICE_ID)
                        .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                        // .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expectedPayloadJson)
                )
                .andExpect(status().isOk());

        // --- VERIFY ---
        verify(mqttGateway).sendCommand(
                eq(expectedPayloadJson),
                eq("devices/" + TEST_DEVICE_ID + "/commands")
        );
    }

    @Test
    void sendCommandToDevice_Fails_WhenInactive() throws Exception {
        // Arrange
        when(deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue(TEST_DEVICE_ID, TEST_USER_ID))
                .thenReturn(Optional.empty());

        Map<String, Object> commandPayload = Map.of("command", "set_temp", "value", 25);

        // Act & Assert
        mockMvc.perform(post("/api/v1/devices/{deviceId}/command", TEST_DEVICE_ID)
                        .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commandPayload))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDevice_Success() throws Exception {
        when(deviceService.deleteDeviceByUser(TEST_DEVICE_ID, TEST_USER_ID))
                .thenReturn(true);

        mockMvc.perform(delete("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                                .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                        // .with(csrf())
                )
                .andExpect(status().isNoContent());

        // Verify
        verify(deviceService).deleteDeviceByUser(TEST_DEVICE_ID, TEST_USER_ID);
    }

    @Test
    void deleteDevice_Forbidden_WrongOwner() throws Exception {
        String otherUserId = "wrong-user-456";

        when(deviceService.deleteDeviceByUser(TEST_DEVICE_ID, otherUserId))
                .thenReturn(false);

        mockMvc.perform(delete("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                                .with(jwt().jwt(jwt -> jwt.subject(otherUserId)))
                        // .with(csrf())
                )
                .andExpect(status().isNotFound());
    }
}