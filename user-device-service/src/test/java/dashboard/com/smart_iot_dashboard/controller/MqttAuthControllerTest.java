package dashboard.com.smart_iot_dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dashboard.com.smart_iot_dashboard.config.SecurityConfig;
import dashboard.com.smart_iot_dashboard.dto.MqttAuthRequest;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MqttAuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "mqtt.bridge.username=test_bridge",
        "mqtt.bridge.password=test_bridge_pass",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://dummy-issuer.com"
})
class MqttAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeviceRepository deviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoderInternal;

    @MockitoBean
    private JwtDecoder jwtDecoder;


    @Test
    void testAuth_Device_Success() throws Exception {
        MqttAuthRequest authRequest = new MqttAuthRequest();
        authRequest.setUsername("sensor-123");
        authRequest.setPassword("raw-token-abc");

        Device mockDevice = new Device();
        mockDevice.setHashedDeviceToken(passwordEncoderInternal.encode("raw-token-abc"));

        when(deviceRepository.findByDeviceIdAndIsActiveTrue("sensor-123")).thenReturn(Optional.of(mockDevice));

        mockMvc.perform(post("/api/internal/mqtt/auth")
                        // .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void testAuth_Device_WrongToken() throws Exception {
        MqttAuthRequest authRequest = new MqttAuthRequest();
        authRequest.setUsername("sensor-123");
        authRequest.setPassword("wrong-token");

        Device mockDevice = new Device();
        mockDevice.setHashedDeviceToken(passwordEncoderInternal.encode("raw-token-abc"));

        when(deviceRepository.findByDeviceIdAndIsActiveTrue("sensor-123")).thenReturn(Optional.of(mockDevice));

        mockMvc.perform(post("/api/internal/mqtt/auth")
                        // .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAuth_Device_NotFound() throws Exception {
        MqttAuthRequest authRequest = new MqttAuthRequest();
        authRequest.setUsername("sensor-404");
        authRequest.setPassword("any-token");

        when(deviceRepository.findByDeviceIdAndIsActiveTrue("sensor-404")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/internal/mqtt/auth")
                        // .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAuth_Bridge_Success() throws Exception {
        MqttAuthRequest authRequest = new MqttAuthRequest();
        authRequest.setUsername("test_bridge");
        authRequest.setPassword("test_bridge_pass");

        mockMvc.perform(post("/api/internal/mqtt/auth")
                        // .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void testAuth_Bridge_WrongPassword() throws Exception {
        MqttAuthRequest authRequest = new MqttAuthRequest();
        authRequest.setUsername("test_bridge");
        authRequest.setPassword("wrong-password");

        mockMvc.perform(post("/api/internal/mqtt/auth")
                        // .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized());
    }
}