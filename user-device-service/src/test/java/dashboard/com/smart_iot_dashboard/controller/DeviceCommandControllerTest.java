package dashboard.com.smart_iot_dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dashboard.com.smart_iot_dashboard.config.SecurityConfig;
import dashboard.com.smart_iot_dashboard.dto.DeviceDTO;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import dashboard.com.smart_iot_dashboard.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        DeviceCommandController.class,
        ProvisioningController.class,
        MqttAclController.class,
        MqttAuthController.class
})
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://dummy-issuer.com"
})
@DisplayName("DeviceCommandController Tests")
class DeviceCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MqttGateway mqttGateway;

    @MockitoBean
    private DeviceService deviceService;

    @MockitoBean
    private ProvisioningService provisioningService;

    @MockitoBean
    private MqttAclService  mqttAclService;

    @MockitoBean
    private MqttAuthService mqttAuthService;

    @MockitoBean
    private DeviceRepository deviceRepository;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String TEST_USER_ID = "user-jwt-subject-123";
    private static final String TEST_DEVICE_ID = "device-abc-456";
    private static final String OTHER_USER_ID = "other-user-789";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        reset(deviceService);
    }

    // ==================== GET /api/v1/devices ====================
    @Nested
    @DisplayName("GET /api/v1/devices - Get User Devices")
    class GetDevicesTests {

        @Test
        @DisplayName("Should return list of devices for authenticated user")
        void getDevices_Success() throws Exception {
            // Arrange
            DeviceDTO device1 = DeviceDTO.builder()
                    .deviceId("device-1")
                    .name("Living Room Thermostat")
                    .isActive(true)
                    .build();

            DeviceDTO device2 = DeviceDTO.builder()
                    .deviceId("device-2")
                    .name("Bedroom Thermostat")
                    .isActive(true)
                    .build();

            when(deviceService.findAllDevicesByUserIdAndIsActiveTrue(TEST_USER_ID))
                    .thenReturn(List.of(device1, device2));

            // Act & Assert
            mockMvc.perform(get("/api/v1/devices")
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].deviceId").value("device-1"))
                    .andExpect(jsonPath("$[0].name").value("Living Room Thermostat"))
                    .andExpect(jsonPath("$[1].deviceId").value("device-2"));

            verify(deviceService).findAllDevicesByUserIdAndIsActiveTrue(TEST_USER_ID);
        }

        @Test
        @DisplayName("Should return empty list when user has no devices")
        void getDevices_EmptyList() throws Exception {
            // Arrange
            when(deviceService.findAllDevicesByUserIdAndIsActiveTrue(TEST_USER_ID))
                    .thenReturn(List.of());

            // Act & Assert
            mockMvc.perform(get("/api/v1/devices")
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void getDevices_Unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/devices"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== DELETE /api/v1/devices/{deviceId} ====================
    @Nested
    @DisplayName("DELETE /api/v1/devices/{deviceId} - Delete Device")
    class DeleteDeviceTests {

        @Test
        @DisplayName("Should delete device successfully when owned by user")
        void deleteDevice_Success() throws Exception {
            // Arrange
            when(deviceService.deleteDeviceByUser(TEST_DEVICE_ID, TEST_USER_ID))
                    .thenReturn(true);

            // Act & Assert
            mockMvc.perform(delete("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID))))
                    .andExpect(status().isNoContent());

            verify(deviceService).deleteDeviceByUser(TEST_DEVICE_ID, TEST_USER_ID);
        }

        @Test
        @DisplayName("Should return 404 when device not found")
        void deleteDevice_NotFound() throws Exception {
            // Arrange
            when(deviceService.deleteDeviceByUser(TEST_DEVICE_ID, TEST_USER_ID))
                    .thenReturn(false);

            // Act & Assert
            mockMvc.perform(delete("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 when trying to delete device owned by another user")
        void deleteDevice_Forbidden_WrongOwner() throws Exception {
            // Arrange
            when(deviceService.deleteDeviceByUser(TEST_DEVICE_ID, OTHER_USER_ID))
                    .thenReturn(false);

            // Act & Assert
            mockMvc.perform(delete("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(OTHER_USER_ID))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void deleteDevice_Unauthorized() throws Exception {
            mockMvc.perform(delete("/api/v1/devices/{deviceId}", TEST_DEVICE_ID))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== PATCH /api/v1/devices/{deviceId} ====================
    @Nested
    @DisplayName("PATCH /api/v1/devices/{deviceId} - Update Device Name")
    class UpdateDeviceNameTests {

        @Test
        @DisplayName("Should update device name successfully")
        void updateDeviceName_Success() throws Exception {
            // Arrange
            String newName = "Kitchen Thermostat";
            Map<String, String> requestBody = Map.of("name", newName);

            DeviceDTO updatedDevice = DeviceDTO.builder()
                    .deviceId(TEST_DEVICE_ID)
                    .name(newName)
                    .isActive(true)
                    .build();

            when(deviceService.updateDeviceName(TEST_DEVICE_ID, TEST_USER_ID, newName))
                    .thenReturn(updatedDevice);

            // Act & Assert
            mockMvc.perform(patch("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deviceId").value(TEST_DEVICE_ID))
                    .andExpect(jsonPath("$.name").value(newName));

            verify(deviceService).updateDeviceName(TEST_DEVICE_ID, TEST_USER_ID, newName);
        }

        @Test
        @DisplayName("Should return 400 when name is missing")
        void updateDeviceName_MissingName() throws Exception {
            // Arrange
            Map<String, String> requestBody = Map.of();

            // Act & Assert
            mockMvc.perform(patch("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isBadRequest());

            verify(deviceService, never()).updateDeviceName(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should return 400 when name is blank")
        void updateDeviceName_BlankName() throws Exception {
            // Arrange
            Map<String, String> requestBody = Map.of("name", "   ");

            // Act & Assert
            mockMvc.perform(patch("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isBadRequest());

            verify(deviceService, never()).updateDeviceName(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should return 400 when name is empty string")
        void updateDeviceName_EmptyName() throws Exception {
            // Arrange
            Map<String, String> requestBody = Map.of("name", "");

            // Act & Assert
            mockMvc.perform(patch("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 404 when device not found")
        void updateDeviceName_DeviceNotFound() throws Exception {
            // Arrange
            String newName = "New Name";
            Map<String, String> requestBody = Map.of("name", newName);

            when(deviceService.updateDeviceName(TEST_DEVICE_ID, TEST_USER_ID, newName))
                    .thenThrow(new RuntimeException("Device not found"));

            // Act & Assert
            mockMvc.perform(patch("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void updateDeviceName_Unauthorized() throws Exception {
            Map<String, String> requestBody = Map.of("name", "New Name");

            mockMvc.perform(patch("/api/v1/devices/{deviceId}", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== POST /api/v1/devices/{deviceId}/command/temperature ====================
    @Nested
    @DisplayName("POST /api/v1/devices/{deviceId}/command/temperature - Set Temperature")
    class SetTemperatureTests {

        @Test
        @DisplayName("Should set temperature successfully with integer value")
        void setTemperature_Success_IntegerValue() throws Exception {
            // Arrange
            Map<String, Object> requestBody = Map.of("value", 22);
            doNothing().when(deviceService).updateTargetTemperature(TEST_DEVICE_ID, TEST_USER_ID, 22.0);

            // Act & Assert
            mockMvc.perform(post("/api/v1/devices/{deviceId}/command/temperature", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            verify(deviceService).updateTargetTemperature(TEST_DEVICE_ID, TEST_USER_ID, 22.0);
        }

        @Test
        @DisplayName("Should set temperature successfully with double value")
        void setTemperature_Success_DoubleValue() throws Exception {
            // Arrange
            Map<String, Object> requestBody = Map.of("value", 22.5);
            doNothing().when(deviceService).updateTargetTemperature(TEST_DEVICE_ID, TEST_USER_ID, 22.5);

            // Act & Assert
            mockMvc.perform(post("/api/v1/devices/{deviceId}/command/temperature", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            verify(deviceService).updateTargetTemperature(TEST_DEVICE_ID, TEST_USER_ID, 22.5);
        }

        @Test
        @DisplayName("Should set temperature successfully with string number")
        void setTemperature_Success_StringValue() throws Exception {
            // Arrange
            Map<String, Object> requestBody = Map.of("value", "23.0");
            doNothing().when(deviceService).updateTargetTemperature(TEST_DEVICE_ID, TEST_USER_ID, 23.0);

            // Act & Assert
            mockMvc.perform(post("/api/v1/devices/{deviceId}/command/temperature", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            verify(deviceService).updateTargetTemperature(TEST_DEVICE_ID, TEST_USER_ID, 23.0);
        }

        @Test
        @DisplayName("Should return 400 when value is missing")
        void setTemperature_MissingValue() throws Exception {
            // Arrange
            Map<String, Object> requestBody = Map.of();

            // Act & Assert
            mockMvc.perform(post("/api/v1/devices/{deviceId}/command/temperature", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isBadRequest());

            verify(deviceService, never()).updateTargetTemperature(anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("Should return 400 when value is not a number")
        void setTemperature_InvalidValue() throws Exception {
            // Arrange
            Map<String, Object> requestBody = Map.of("value", "not-a-number");

            // Act & Assert
            mockMvc.perform(post("/api/v1/devices/{deviceId}/command/temperature", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isBadRequest());

            verify(deviceService, never()).updateTargetTemperature(anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("Should return 404 when device not found")
        void setTemperature_DeviceNotFound() throws Exception {
            // Arrange
            Map<String, Object> requestBody = Map.of("value", 22.0);
            doThrow(new dashboard.com.smart_iot_dashboard.exception.DeviceNotFoundException("Device not found"))
                    .when(deviceService)
                    .updateTargetTemperature(TEST_DEVICE_ID, TEST_USER_ID, 22.0);

            // Act & Assert
            mockMvc.perform(post("/api/v1/devices/{deviceId}/command/temperature", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void setTemperature_Unauthorized() throws Exception {
            Map<String, Object> requestBody = Map.of("value", 22.0);

            mockMvc.perform(post("/api/v1/devices/{deviceId}/command/temperature", TEST_DEVICE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should handle negative temperature values")
        void setTemperature_NegativeValue() throws Exception {
            // Arrange
            Map<String, Object> requestBody = Map.of("value", -5.0);
            doNothing().when(deviceService).updateTargetTemperature(TEST_DEVICE_ID, TEST_USER_ID, -5.0);

            // Act & Assert
            mockMvc.perform(post("/api/v1/devices/{deviceId}/command/temperature", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            verify(deviceService).updateTargetTemperature(TEST_DEVICE_ID, TEST_USER_ID, -5.0);
        }

        @Test
        @DisplayName("Should handle very large temperature values")
        void setTemperature_LargeValue() throws Exception {
            // Arrange
            Map<String, Object> requestBody = Map.of("value", 100.0);
            doNothing().when(deviceService).updateTargetTemperature(TEST_DEVICE_ID, TEST_USER_ID, 100.0);

            // Act & Assert
            mockMvc.perform(post("/api/v1/devices/{deviceId}/command/temperature", TEST_DEVICE_ID)
                            .with(jwt().jwt(jwt -> jwt.subject(TEST_USER_ID)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            verify(deviceService).updateTargetTemperature(TEST_DEVICE_ID, TEST_USER_ID, 100.0);
        }
    }
}