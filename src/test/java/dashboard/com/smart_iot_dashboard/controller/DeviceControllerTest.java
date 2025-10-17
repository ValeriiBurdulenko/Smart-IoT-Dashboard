package dashboard.com.smart_iot_dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.security.JwtUtil; // ДОБАВЛЕН импорт
import dashboard.com.smart_iot_dashboard.service.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService; // ДОБАВЛЕН импорт
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DeviceController.class)
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

    @Test
    void whenAccessDevicesWithoutAuth_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser") // Имитирует залогиненного пользователя
    void whenGetAllDevicesAsAuthenticatedUser_thenReturns200() throws Exception {
        // Arrange
        Device device = new Device();
        device.setId(1L);
        device.setName("My Device");
        when(deviceService.findAllDevicesForCurrentUser()).thenReturn(Collections.singletonList(device));

        // Act & Assert
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("My Device"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void whenGetDeviceById_andDeviceExists_thenReturns200() throws Exception {
        // Arrange
        Device device = new Device();
        device.setId(1L);
        when(deviceService.findDeviceByIdForCurrentUser(1L)).thenReturn(Optional.of(device));

        // Act & Assert
        mockMvc.perform(get("/api/v1/devices/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "testuser")
    void whenGetDeviceById_andDeviceNotExists_thenReturns404() throws Exception {
        // Arrange
        when(deviceService.findDeviceByIdForCurrentUser(1L)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/devices/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "testuser")
    void whenCreateDevice_thenReturns201() throws Exception {
        // Arrange
        Device newDevice = new Device();
        newDevice.setName("New Thermostat");

        Device savedDevice = new Device();
        savedDevice.setId(1L);
        savedDevice.setName("New Thermostat");

        when(deviceService.createDevice(any(Device.class))).thenReturn(savedDevice);

        // Act & Assert
        mockMvc.perform(post("/api/v1/devices")
                        .with(csrf()) // CSRF-токен нужен для POST-запросов в тестах Spring Security
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newDevice)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/devices/1"));
    }
}