package dashboard.com.smart_iot_dashboard; // Или ваш основной пакет

import com.fasterxml.jackson.databind.ObjectMapper;
import dashboard.com.smart_iot_dashboard.dto.AuthResponse;
import dashboard.com.smart_iot_dashboard.dto.LoginRequest;
import dashboard.com.smart_iot_dashboard.dto.RegisterRequest;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class UserDeviceFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceRepository deviceRepository;

    @Test
    void shouldRegisterLoginAndCreateDevice() throws Exception {
        String username = "integration_tester_" + System.currentTimeMillis();
        String password = "password123";
        String externalDeviceId = "int-test-device-" + System.currentTimeMillis();

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setPassword(password);
        registerRequest.setRole("ROLE_USER");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(responseBody, AuthResponse.class);
        String token = authResponse.getToken();
        assertThat(token).isNotBlank();

        Device newDevice = new Device();
        newDevice.setExternalId(externalDeviceId);
        newDevice.setName("Integration Test Device");
        newDevice.setLocation("Test Lab");
        newDevice.setStatus("ACTIVE");

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newDevice)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalId").value(externalDeviceId));


        assertThat(deviceRepository.findByExternalId(externalDeviceId)).isPresent();
    }
}