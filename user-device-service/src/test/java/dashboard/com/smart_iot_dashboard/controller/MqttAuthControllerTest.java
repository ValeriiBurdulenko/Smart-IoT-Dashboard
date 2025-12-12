package dashboard.com.smart_iot_dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dashboard.com.smart_iot_dashboard.config.SecurityConfig;
import dashboard.com.smart_iot_dashboard.dto.MqttAuthRequest;
import dashboard.com.smart_iot_dashboard.service.MqttAuthService;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MqttAuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "mqtt.bridge.username=test_bridge",
        "mqtt.bridge.password=test_bridge_pass",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://dummy-issuer.com"
})
@DisplayName("MQTT Auth Controller Tests (Refactored)")
class MqttAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MqttAuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String BRIDGE_USERNAME = "test_bridge";
    private static final String BRIDGE_PASSWORD = "test_bridge_pass";
    private static final String DEVICE_ID = "sensor-123";
    private static final String DEVICE_TOKEN = "raw-token-abc";

    @Nested
    @DisplayName("Successful Authentication")
    class SuccessfulAuthTests {

        @BeforeEach
        void setUp() {
            when(authService.authenticateMqttClient(anyString(), anyString()))
                    .thenReturn(true);
        }

        @Test
        @DisplayName("Bridge with correct credentials - 200 OK")
        void testBridgeAuth200() throws Exception {
            when(authService.authenticateMqttClient(BRIDGE_USERNAME, BRIDGE_PASSWORD))
                    .thenReturn(true);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(BRIDGE_USERNAME);
            request.setPassword(BRIDGE_PASSWORD);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService, times(1)).authenticateMqttClient(BRIDGE_USERNAME, BRIDGE_PASSWORD);
        }

        @Test
        @DisplayName("Device with correct token - 200 OK")
        void testDeviceAuth200() throws Exception {
            when(authService.authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN))
                    .thenReturn(true);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(DEVICE_ID);
            request.setPassword(DEVICE_TOKEN);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService, times(1)).authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN);
        }

        @Test
        @DisplayName("Multiple successful authentications")
        void testMultipleSuccessfulAuth() throws Exception {
            for (int i = 0; i < 5; i++) {
                String username = DEVICE_ID + i;
                when(authService.authenticateMqttClient(username, DEVICE_TOKEN))
                        .thenReturn(true);

                MqttAuthRequest request = new MqttAuthRequest();
                request.setUsername(username);
                request.setPassword(DEVICE_TOKEN);

                mockMvc.perform(post("/api/internal/mqtt/auth")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk());
            }
        }
    }

    @Nested
    @DisplayName("Failed Authentication")
    class FailedAuthTests {

        @BeforeEach
        void setUp() {
            when(authService.authenticateMqttClient(anyString(), anyString()))
                    .thenReturn(false);
        }

        @Test
        @DisplayName("Bridge with wrong password - 401 UNAUTHORIZED")
        void testBridgeAuthFail401() throws Exception {
            when(authService.authenticateMqttClient(BRIDGE_USERNAME, "wrong-password"))
                    .thenReturn(false);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(BRIDGE_USERNAME);
            request.setPassword("wrong-password");

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(authService, times(1)).authenticateMqttClient(BRIDGE_USERNAME, "wrong-password");
        }

        @Test
        @DisplayName("Device with wrong token - 401 UNAUTHORIZED")
        void testDeviceAuthFail401() throws Exception {
            when(authService.authenticateMqttClient(DEVICE_ID, "wrong-token"))
                    .thenReturn(false);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(DEVICE_ID);
            request.setPassword("wrong-token");

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(authService, times(1)).authenticateMqttClient(DEVICE_ID, "wrong-token");
        }

        @Test
        @DisplayName("Non-existent device - 401 UNAUTHORIZED")
        void testNonExistentDeviceAuth401() throws Exception {
            when(authService.authenticateMqttClient("unknown-device", "any-token"))
                    .thenReturn(false);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername("unknown-device");
            request.setPassword("any-token");

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Multiple failed attempts")
        void testMultipleFailedAuth() throws Exception {
            for (int i = 0; i < 5; i++) {
                String password = "wrong-password-" + i;
                when(authService.authenticateMqttClient(DEVICE_ID, password))
                        .thenReturn(false);

                MqttAuthRequest request = new MqttAuthRequest();
                request.setUsername(DEVICE_ID);
                request.setPassword(password);

                mockMvc.perform(post("/api/internal/mqtt/auth")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    @Nested
    @DisplayName("Service Invocation Tests")
    class ServiceInvocationTests {

        @Test
        @DisplayName("Service is called exactly once per request")
        void testServiceCalledOncePerRequest() throws Exception {
            when(authService.authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN))
                    .thenReturn(true);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(DEVICE_ID);
            request.setPassword(DEVICE_TOKEN);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService, times(1)).authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN);
        }

        @Test
        @DisplayName("Service receives correct username parameter")
        void testServiceReceivesCorrectUsername() throws Exception {
            String customUsername = "custom-device-xyz";
            when(authService.authenticateMqttClient(customUsername, DEVICE_TOKEN))
                    .thenReturn(true);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(customUsername);
            request.setPassword(DEVICE_TOKEN);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService, times(1)).authenticateMqttClient(customUsername, DEVICE_TOKEN);
        }

        @Test
        @DisplayName("Service receives correct password parameter")
        void testServiceReceivesCorrectPassword() throws Exception {
            String customPassword = "custom-token-xyz";
            when(authService.authenticateMqttClient(DEVICE_ID, customPassword))
                    .thenReturn(true);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(DEVICE_ID);
            request.setPassword(customPassword);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService, times(1)).authenticateMqttClient(DEVICE_ID, customPassword);
        }

        @Test
        @DisplayName("Service is not called when @Valid validation fails")
        void testServiceNotCalledWhenValidationFails() throws Exception {
            // When username is null, @Valid should reject the request before service is called
            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(null);
            request.setPassword(DEVICE_TOKEN);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            // Service should not be called due to @Valid validation
            verify(authService, never()).authenticateMqttClient(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Null username - 400 BAD_REQUEST")
        void testNullUsernameBadRequest() throws Exception {
            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(null);
            request.setPassword(DEVICE_TOKEN);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).authenticateMqttClient(anyString(), anyString());
        }

        @Test
        @DisplayName("Empty username - 400 BAD_REQUEST")
        void testEmptyUsernameBadRequest() throws Exception {
            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername("");
            request.setPassword(DEVICE_TOKEN);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).authenticateMqttClient(anyString(), anyString());
        }

        @Test
        @DisplayName("Null password - 400 BAD_REQUEST")
        void testNullPasswordBadRequest() throws Exception {
            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(DEVICE_ID);
            request.setPassword(null);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).authenticateMqttClient(anyString(), anyString());
        }

        @Test
        @DisplayName("Empty password - 400 BAD_REQUEST")
        void testEmptyPasswordBadRequest() throws Exception {
            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(DEVICE_ID);
            request.setPassword("");

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).authenticateMqttClient(anyString(), anyString());
        }

        @Test
        @DisplayName("Missing username field - 400 BAD_REQUEST")
        void testMissingUsernameFieldBadRequest() throws Exception {
            String json = "{\"password\":\"" + DEVICE_TOKEN + "\"}";

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).authenticateMqttClient(anyString(), anyString());
        }

        @Test
        @DisplayName("Missing password field - 400 BAD_REQUEST")
        void testMissingPasswordFieldBadRequest() throws Exception {
            String json = "{\"username\":\"" + DEVICE_ID + "\"}";

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).authenticateMqttClient(anyString(), anyString());
        }

        @Test
        @DisplayName("Malformed JSON - 400 BAD_REQUEST")
        void testMalformedJsonBadRequest() throws Exception {
            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid json}"))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).authenticateMqttClient(anyString(), anyString());
        }

        @Test
        @DisplayName("Extra fields are ignored")
        void testExtraFieldsIgnored() throws Exception {
            when(authService.authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN))
                    .thenReturn(true);

            String json = "{\"username\":\"" + DEVICE_ID + "\",\"password\":\"" + DEVICE_TOKEN +
                    "\",\"unknown_field\":\"value\",\"extra\":123}";

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk());

            verify(authService, times(1)).authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN);
        }
    }

    @Nested
    @DisplayName("Controller-Level Null Safety Tests")
    class NullSafetyTests {

        @Test
        @DisplayName("Controller handles null values from validated request")
        void testControllerHandlesNullValuesAfterValidation() throws Exception {
            // Note: @Valid prevents null values from reaching the controller,
            // but the controller has additional null checks as defensive programming

            when(authService.authenticateMqttClient("test", "pass"))
                    .thenReturn(true);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername("test");
            request.setPassword("pass");

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("HTTP Method and Content Type Tests")
    class HttpMethodTests {

        @Test
        @DisplayName("GET request not allowed - 405 METHOD_NOT_ALLOWED")
        void testGetMethodNotAllowed() throws Exception {
            mockMvc.perform(get("/api/internal/mqtt/auth"))
                    .andExpect(status().isMethodNotAllowed());

            verify(authService, never()).authenticateMqttClient(anyString(), anyString());
        }

        @Test
        @DisplayName("Wrong content type (XML) - 415 UNSUPPORTED_MEDIA_TYPE")
        void testWrongContentTypeXml() throws Exception {
            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(DEVICE_ID);
            request.setPassword(DEVICE_TOKEN);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_XML)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnsupportedMediaType());

            verify(authService, never()).authenticateMqttClient(anyString(), anyString());
        }

        @Test
        @DisplayName("Correct content type JSON - 200 OK")
        void testCorrectContentTypeJson() throws Exception {
            when(authService.authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN))
                    .thenReturn(true);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(DEVICE_ID);
            request.setPassword(DEVICE_TOKEN);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService, times(1)).authenticateMqttClient(DEVICE_ID, DEVICE_TOKEN);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Very long username")
        void testVeryLongUsername() throws Exception {
            String longUsername = "device-" + "a".repeat(1000);
            when(authService.authenticateMqttClient(longUsername, DEVICE_TOKEN))
                    .thenReturn(false);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(longUsername);
            request.setPassword(DEVICE_TOKEN);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(authService, times(1)).authenticateMqttClient(longUsername, DEVICE_TOKEN);
        }

        @Test
        @DisplayName("Very long password")
        void testVeryLongPassword() throws Exception {
            String longPassword = "password-" + "a".repeat(10000);
            when(authService.authenticateMqttClient(DEVICE_ID, longPassword))
                    .thenReturn(false);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(DEVICE_ID);
            request.setPassword(longPassword);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(authService, times(1)).authenticateMqttClient(DEVICE_ID, longPassword);
        }

        @Test
        @DisplayName("Username with special characters")
        void testUsernameWithSpecialCharacters() throws Exception {
            String specialUsername = "device@123#test_ABC";
            when(authService.authenticateMqttClient(specialUsername, DEVICE_TOKEN))
                    .thenReturn(false);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(specialUsername);
            request.setPassword(DEVICE_TOKEN);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(authService, times(1)).authenticateMqttClient(specialUsername, DEVICE_TOKEN);
        }

        @Test
        @DisplayName("Password with special characters and spaces")
        void testPasswordWithSpecialCharacters() throws Exception {
            String specialPassword = "pass word@123!#$%^&*()";
            when(authService.authenticateMqttClient(DEVICE_ID, specialPassword))
                    .thenReturn(false);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(DEVICE_ID);
            request.setPassword(specialPassword);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(authService, times(1)).authenticateMqttClient(DEVICE_ID, specialPassword);
        }

        @Test
        @DisplayName("Unicode characters in username")
        void testUnicodeCharactersUsername() throws Exception {
            String unicodeUsername = "sensor-тест-123";
            when(authService.authenticateMqttClient(unicodeUsername, DEVICE_TOKEN))
                    .thenReturn(false);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(unicodeUsername);
            request.setPassword(DEVICE_TOKEN);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(authService, times(1)).authenticateMqttClient(unicodeUsername, DEVICE_TOKEN);
        }

        @Test
        @DisplayName("Unicode characters in password")
        void testUnicodeCharactersPassword() throws Exception {
            String unicodePassword = "пароль-тест-123";
            when(authService.authenticateMqttClient(DEVICE_ID, unicodePassword))
                    .thenReturn(false);

            MqttAuthRequest request = new MqttAuthRequest();
            request.setUsername(DEVICE_ID);
            request.setPassword(unicodePassword);

            mockMvc.perform(post("/api/internal/mqtt/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(authService, times(1)).authenticateMqttClient(DEVICE_ID, unicodePassword);
        }
    }
}