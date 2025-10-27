package dashboard.com.smart_iot_dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dashboard.com.smart_iot_dashboard.config.SecurityConfig; // Импортируем SecurityConfig
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.security.JwtUtil;
import dashboard.com.smart_iot_dashboard.service.DeviceService;
import dashboard.com.smart_iot_dashboard.service.MqttGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import; // Импорт для @Import
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DeviceController.class)
@Import(SecurityConfig.class)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeviceService deviceService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private MqttGateway mqttGateway;

    @Test
    void whenAccessDevicesWithoutAuth_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void whenGetAllDevicesAsAuthenticatedUser_thenReturns200() throws Exception {
        // Arrange
        Device device = new Device();
        device.setExternalId("sensor-abc-123");
        device.setName("My Device");
        when(deviceService.findAllDevicesForCurrentUser()).thenReturn(Collections.singletonList(device));

        // Act & Assert
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("My Device"));
    }

    @Test
    @WithMockUser
    void whenGetDeviceByExternalId_andDeviceExists_thenReturns200() throws Exception {
        // Arrange
        String externalId = "sensor-abc-123";
        Device device = new Device();
        device.setExternalId(externalId);
        device.setName("Test Device");
        when(deviceService.findDeviceByExternalIdForCurrentUser(externalId)).thenReturn(Optional.of(device));

        // Act & Assert
        mockMvc.perform(get("/api/v1/devices/{externalId}", externalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Device"));
    }

    @Test
    @WithMockUser
    void whenGetDeviceByExternalId_andDeviceNotExists_thenReturns404() throws Exception {
        // Arrange
        String externalId = "sensor-not-found-456";
        when(deviceService.findDeviceByExternalIdForCurrentUser(externalId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/devices/{externalId}", externalId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void whenCreateDevice_thenReturns201() throws Exception {
        // Arrange
        Device newDevice = new Device();
        newDevice.setExternalId("sensor-new-789");
        newDevice.setName("New Thermostat");

        when(deviceService.createDevice(any(Device.class))).thenReturn(newDevice);

        // Act & Assert
        mockMvc.perform(post("/api/v1/devices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newDevice)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/devices/sensor-new-789"));
    }

    @Test
    @WithMockUser
    void whenSendCommand_andDeviceExists_thenReturns200() throws Exception {
        // Arrange
        String externalId = "sensor-cmd-111";
        Device mockDevice = new Device();
        mockDevice.setExternalId(externalId);

        when(deviceService.findDeviceByExternalIdForCurrentUser(externalId)).thenReturn(Optional.of(mockDevice));
        String commandPayload = "{\"command\":\"reboot\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/devices/{externalId}/commands", externalId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandPayload))
                .andExpect(status().isOk());

        verify(mqttGateway).sendCommand(eq(commandPayload), eq("devices/" + externalId + "/commands"));
    }
}