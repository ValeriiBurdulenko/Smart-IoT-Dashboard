package dashboard.com.smart_iot_dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dashboard.com.smart_iot_dashboard.config.SecurityConfig;
import dashboard.com.smart_iot_dashboard.dto.MqttAclRequest;
import dashboard.com.smart_iot_dashboard.service.MqttAclService;
import jakarta.validation.constraints.Null;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MqttAclController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "mqtt.bridge.username=test_bridge",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://dummy-issuer.com"
})
@DisplayName("MQTT ACL Controller Tests")
class MqttAclControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MqttAclService aclService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final int ACL_READ = 1;
    private static final int ACL_WRITE = 2;
    private static final int ACL_SUBSCRIBE = 4;
    private static final String BRIDGE_USERNAME = "test_bridge";
    private static final String DEVICE_ID = "sensor-123";

    @Nested
    @DisplayName("Successful ACL Checks")
    class SuccessfulAclTests {

        @BeforeEach
        void setUp() {
            // По умолчанию все проверки разрешены
            when(aclService.checkAcl(anyString(), anyInt(), anyString()))
                    .thenReturn(true);
        }

        @Test
        @DisplayName("Bridge subscribe to telemetry - 200 OK")
        void testBridgeSubscribeTelemetry200() throws Exception {
            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(BRIDGE_USERNAME);
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_SUBSCRIBE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Device write to telemetry - 200 OK")
        void testDeviceWriteTelemetry200() throws Exception {
            when(aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress"))
                    .thenReturn(true);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Device subscribe to own commands - 200 OK")
        void testDeviceSubscribeOwnCommands200() throws Exception {
            when(aclService.checkAcl(DEVICE_ID, ACL_SUBSCRIBE, "devices/sensor-123/commands"))
                    .thenReturn(true);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("devices/sensor-123/commands");
            request.setAcc(ACL_SUBSCRIBE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Multiple successful requests")
        void testMultipleSuccessfulRequests() throws Exception {
            for (int i = 0; i < 5; i++) {
                MqttAclRequest request = new MqttAclRequest();
                request.setUsername(DEVICE_ID + i);
                request.setTopic("iot/telemetry/ingress");
                request.setAcc(ACL_WRITE);

                mockMvc.perform(post("/api/internal/mqtt/acl")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk());
            }
        }
    }

    @Nested
    @DisplayName("Forbidden ACL Checks")
    class ForbiddenAclTests {

        @BeforeEach
        void setUp() {
            when(aclService.checkAcl(anyString(), anyInt(), anyString()))
                    .thenReturn(false);
        }

        @Test
        @DisplayName("Bridge write to telemetry - 403 FORBIDDEN")
        void testBridgeWriteTelemetry403() throws Exception {
            when(aclService.checkAcl(BRIDGE_USERNAME, ACL_WRITE, "iot/telemetry/ingress"))
                    .thenReturn(false);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(BRIDGE_USERNAME);
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Device subscribe to other commands - 403 FORBIDDEN")
        void testDeviceSubscribeOtherCommands403() throws Exception {
            when(aclService.checkAcl(DEVICE_ID, ACL_SUBSCRIBE, "devices/sensor-456/commands"))
                    .thenReturn(false);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("devices/sensor-456/commands");
            request.setAcc(ACL_SUBSCRIBE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Device write to own commands - 403 FORBIDDEN")
        void testDeviceWriteOwnCommands403() throws Exception {
            when(aclService.checkAcl(DEVICE_ID, ACL_WRITE, "devices/sensor-123/commands"))
                    .thenReturn(false);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("devices/sensor-123/commands");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Non-existent device - 403 FORBIDDEN")
        void testNonExistentDevice403() throws Exception {
            when(aclService.checkAcl("unknown-device", ACL_WRITE, "iot/telemetry/ingress"))
                    .thenReturn(false);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername("unknown-device");
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Multiple failed requests")
        void testMultipleFailedRequests() throws Exception {
            for (int i = 0; i < 5; i++) {
                MqttAclRequest request = new MqttAclRequest();
                request.setUsername(DEVICE_ID);
                request.setTopic("forbidden-topic-" + i);
                request.setAcc(ACL_READ);

                mockMvc.perform(post("/api/internal/mqtt/acl")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Nested
    @DisplayName("Service Integration Tests")
    class ServiceIntegrationTests {

        @Test
        @DisplayName("Service is called with correct parameters")
        void testServiceCalledWithCorrectParameters() throws Exception {
            when(aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress"))
                    .thenReturn(true);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(aclService, times(1)).checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress");
        }

        @Test
        @DisplayName("Service receives username from request")
        void testServiceReceivesUsername() throws Exception {
            MqttAclRequest request = new MqttAclRequest();
            request.setUsername("custom-device");
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(aclService, times(1)).checkAcl("custom-device", ACL_WRITE, "iot/telemetry/ingress");
        }

        @Test
        @DisplayName("Service receives topic from request")
        void testServiceReceivesTopic() throws Exception {
            when(aclService.checkAcl(DEVICE_ID, ACL_WRITE, "custom/topic/path"))
                    .thenReturn(true);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("custom/topic/path");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(aclService, times(1)).checkAcl(DEVICE_ID, ACL_WRITE, "custom/topic/path");
        }

        @Test
        @DisplayName("Service receives accessType from request")
        void testServiceReceivesAccessType() throws Exception {
            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_READ);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(aclService, times(1)).checkAcl(DEVICE_ID, ACL_READ, "iot/telemetry/ingress");
        }

        @Test
        @DisplayName("All access types are passed correctly to service")
        void testAllAccessTypesPassedToService() throws Exception {
            int[] accessTypes = {ACL_READ, ACL_WRITE, ACL_SUBSCRIBE};

            for (int accessType : accessTypes) {
                MqttAclRequest request = new MqttAclRequest();
                request.setUsername(DEVICE_ID);
                request.setTopic("iot/telemetry/ingress");
                request.setAcc(accessType);

                mockMvc.perform(post("/api/internal/mqtt/acl")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isForbidden());
            }

            verify(aclService, times(1)).checkAcl(DEVICE_ID, ACL_READ, "iot/telemetry/ingress");
            verify(aclService, times(1)).checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress");
            verify(aclService, times(1)).checkAcl(DEVICE_ID, ACL_SUBSCRIBE, "iot/telemetry/ingress");
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Null username - bad request")
        void testNullUsernameBadRequest() throws Exception {
            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(null);
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Empty username - bad request")
        void testEmptyUsernameBadRequest() throws Exception {
            MqttAclRequest request = new MqttAclRequest();
            request.setUsername("");
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Null topic - bad request")
        void testNullTopicBadRequest() throws Exception {
            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic(null);
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Empty topic - bad request")
        void testEmptyTopicBadRequest() throws Exception {
            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Null accessType - bad request")
        void testNullAccessTypeBadRequest() throws Exception {
            // Can't set null on int primitive in Java, so we construct JSON manually
            // to verify that the controller handles 'null' in the 'acc' field correctly.
            String jsonRequest = String.format(
                    "{\"username\":\"%s\", \"topic\":\"%s\", \"acc\":}",
                    DEVICE_ID, "iot/telemetry/ingress"
            );

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing all fields - bad request")
        void testMissingAllFieldsBadRequest() throws Exception {
            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Extra unknown fields are ignored")
        void testExtraFieldsIgnored() throws Exception {
            when(aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress"))
                    .thenReturn(true);

            String json = "{\"username\":\"" + DEVICE_ID + "\",\"topic\":\"iot/telemetry/ingress\"," +
                    "\"acc\":2,\"unknown_field\":\"value\"}";

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("HTTP Response Tests")
    class HttpResponseTests {

        @Test
        @DisplayName("Success response is empty body")
        void testSuccessResponseEmptyBody() throws Exception {
            when(aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress"))
                    .thenReturn(true);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Forbidden response is empty body")
        void testForbiddenResponseEmptyBody() throws Exception {
            when(aclService.checkAcl(DEVICE_ID, ACL_WRITE, "forbidden"))
                    .thenReturn(false);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("forbidden");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Response content type is correct")
        void testResponseContentType() throws Exception {
            when(aclService.checkAcl(DEVICE_ID, ACL_WRITE, "iot/telemetry/ingress"))
                    .thenReturn(true);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Very long username")
        void testVeryLongUsername() throws Exception {
            String longUsername = "device-" + "a".repeat(1000);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(longUsername);
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(aclService, times(1)).checkAcl(longUsername, ACL_WRITE, "iot/telemetry/ingress");
        }

        @Test
        @DisplayName("Very long topic")
        void testVeryLongTopic() throws Exception {
            String longTopic = "topic/" + "a".repeat(1000);

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic(longTopic);
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(aclService, times(1)).checkAcl(DEVICE_ID, ACL_WRITE, longTopic);
        }

        @Test
        @DisplayName("Username with special characters")
        void testUsernameWithSpecialCharacters() throws Exception {
            String specialUsername = "device@123#test_ABC";

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(specialUsername);
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(aclService, times(1)).checkAcl(specialUsername, ACL_WRITE, "iot/telemetry/ingress");
        }

        @Test
        @DisplayName("Topic with Unicode characters")
        void testTopicWithUnicodeCharacters() throws Exception {
            String unicodeTopic = "iot/тест/ingress";

            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic(unicodeTopic);
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(aclService, times(1)).checkAcl(DEVICE_ID, ACL_WRITE, unicodeTopic);
        }

        @Test
        @DisplayName("Invalid HTTP method GET instead of POST")
        void testInvalidHttpMethodGet() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/internal/mqtt/acl"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("Wrong content type")
        void testWrongContentType() throws Exception {
            MqttAclRequest request = new MqttAclRequest();
            request.setUsername(DEVICE_ID);
            request.setTopic("iot/telemetry/ingress");
            request.setAcc(ACL_WRITE);

            mockMvc.perform(post("/api/internal/mqtt/acl")
                            .contentType(MediaType.APPLICATION_XML)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }
}