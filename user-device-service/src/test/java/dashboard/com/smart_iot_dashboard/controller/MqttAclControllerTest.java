package dashboard.com.smart_iot_dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dashboard.com.smart_iot_dashboard.config.SecurityConfig;
import dashboard.com.smart_iot_dashboard.dto.MqttAclRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource; // Für @Value Felder
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MqttAclController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "mqtt.bridge.username=test_bridge",
        // Füge die (Dummy) Issuer-URI hinzu, die SecurityConfig benötigt
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://dummy-issuer.com"
})
class MqttAclControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;


    private static final int ACL_READ = 1;
    private static final int ACL_WRITE = 2;
    private static final int ACL_SUBSCRIBE = 4;

    // --- Bridge Tests ---

    @Test
    void testAcl_Bridge_SubscribeTelemetry_Success() throws Exception {
        MqttAclRequest aclRequest = new MqttAclRequest();
        aclRequest.setUsername("test_bridge"); // Der Bridge-Benutzer
        aclRequest.setTopic("iot/telemetry/ingress");
        aclRequest.setAcc(ACL_SUBSCRIBE); // Versucht zu abonnieren

        mockMvc.perform(post("/api/internal/mqtt/acl")
                        //.with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aclRequest)))
                .andExpect(status().isOk()); // Erwartet 200 OK
    }

    @Test
    void testAcl_Bridge_WriteToCommands_Forbidden() throws Exception {
        MqttAclRequest aclRequest = new MqttAclRequest();
        aclRequest.setUsername("test_bridge");
        aclRequest.setTopic("devices/some-device/commands");
        aclRequest.setAcc(ACL_WRITE); // Versucht zu schreiben

        mockMvc.perform(post("/api/internal/mqtt/acl")
                        //.with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aclRequest)))
                .andExpect(status().isForbidden()); // Erwartet 403 Forbidden
    }

    // --- Device Tests ---

    @Test
    void testAcl_Device_WriteTelemetry_Success() throws Exception {
        MqttAclRequest aclRequest = new MqttAclRequest();
        aclRequest.setUsername("sensor-123"); // Ein normales Gerät
        aclRequest.setTopic("iot/telemetry/ingress");
        aclRequest.setAcc(ACL_WRITE); // Versucht zu schreiben

        mockMvc.perform(post("/api/internal/mqtt/acl")
                        //.with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aclRequest)))
                .andExpect(status().isOk()); // Erwartet 200 OK
    }

    @Test
    void testAcl_Device_SubscribeToOwnCommands_Success() throws Exception {
        MqttAclRequest aclRequest = new MqttAclRequest();
        aclRequest.setUsername("sensor-123");
        aclRequest.setTopic("devices/sensor-123/commands"); // Sein eigener Topic
        aclRequest.setAcc(ACL_SUBSCRIBE); // Versucht zu abonnieren

        mockMvc.perform(post("/api/internal/mqtt/acl")
                        //.with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aclRequest)))
                .andExpect(status().isOk()); // Erwartet 200 OK
    }

    @Test
    void testAcl_Device_SubscribeToOtherCommands_Forbidden() throws Exception {
        MqttAclRequest aclRequest = new MqttAclRequest();
        aclRequest.setUsername("sensor-123");
        aclRequest.setTopic("devices/sensor-ABC/commands"); // Ein fremder Topic
        aclRequest.setAcc(ACL_SUBSCRIBE);

        mockMvc.perform(post("/api/internal/mqtt/acl")
                        //.with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aclRequest)))
                .andExpect(status().isForbidden()); // Erwartet 403 Forbidden
    }

    @Test
    void testAcl_Device_WriteToOwnCommands_Forbidden() throws Exception {
        // Geräte sollten nicht in ihre eigenen Befehls-Topics schreiben können
        MqttAclRequest aclRequest = new MqttAclRequest();
        aclRequest.setUsername("sensor-123");
        aclRequest.setTopic("devices/sensor-123/commands");
        aclRequest.setAcc(ACL_WRITE);

        mockMvc.perform(post("/api/internal/mqtt/acl")
                        //.with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aclRequest)))
                .andExpect(status().isForbidden()); // Erwartet 403 Forbidden
    }
}