package dashboard.com.smart_iot_dashboard;

import dashboard.com.smart_iot_dashboard.dto.ClaimResponse;
import dashboard.com.smart_iot_dashboard.dto.GenerateClaimCodeResponse;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import dashboard.com.smart_iot_dashboard.service.MqttGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest // Startet die *gesamte* Spring-Anwendung
@AutoConfigureMockMvc // Stellt MockMvc für HTTP-Anfragen bereit
@Transactional // Rollt alle DB-Änderungen nach jedem Test zurück
@ActiveProfiles("dev") // Stellt sicher, dass wir die H2-Datenbank (aus application-dev.properties) verwenden
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserDeviceFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceRepository deviceRepository; // Das ECHTE Repository (verbunden mit H2)

    // --- Externe Abhängigkeiten MOCKEN ---
    @MockitoBean
    private StringRedisTemplate redisTemplate;
    @MockitoBean
    private ValueOperations<String, String> valueOperations;
    @MockitoBean
    private MqttGateway mqttGateway;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        // Richte den Redis-Mock ein, bevor jeder Test läuft
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void test_Full_Provisioning_And_Command_Flow() throws Exception {

        // --- Teil 1: Code generieren (als eingeloggter Benutzer) ---
        String testUserId = "user-keycloak-id-123";

        MvcResult result = mockMvc.perform(post("/api/v1/devices/generate-claim-code")
                        .with(csrf()) // Fügt CSRF-Token hinzu
                        .with(jwt().jwt(jwt -> jwt.subject(testUserId))) // Simuliert einen Keycloak JWT-Token mit User-ID "test-user-id"
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimCode").exists())
                .andReturn();

        String jsonResponse = result.getResponse().getContentAsString();
        GenerateClaimCodeResponse codeResponse = objectMapper.readValue(jsonResponse, GenerateClaimCodeResponse.class);
        String claimCode = codeResponse.getClaimCode();

        assertThat(claimCode).isNotNull();
        // Überprüfe, ob der Service versucht hat, den Code in Redis zu speichern
        verify(valueOperations).set(eq("claimcode:" + claimCode), eq(testUserId), any(Duration.class));


        // --- Teil 2: Gerät mit Code anmelden (Öffentlicher Endpunkt) ---
        // Simuliere, dass Redis den Code zurückgibt, wenn der Service fragt
        when(valueOperations.get("claimcode:" + claimCode)).thenReturn(testUserId);

        Map<String, String> claimRequest = Map.of("claimCode", claimCode);
        String claimRequestJson = objectMapper.writeValueAsString(claimRequest);

        MvcResult claimResult = mockMvc.perform(post("/api/v1/devices/claim-with-code")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimRequestJson)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").exists())
                .andExpect(jsonPath("$.deviceToken").exists())
                .andReturn();

        jsonResponse = claimResult.getResponse().getContentAsString();
        ClaimResponse claimResponse = objectMapper.readValue(jsonResponse, ClaimResponse.class);
        String newDeviceId = claimResponse.getDeviceId();

        // Überprüfe, ob der Code aus Redis gelöscht wurde
        verify(redisTemplate).delete("claimcode:" + claimCode);

        // Überprüfe, ob das Gerät WIRKLICH in der H2-Datenbank gespeichert wurde
        Optional<Device> savedDeviceOpt = deviceRepository.findByDeviceId(newDeviceId);
        assertThat(savedDeviceOpt).isPresent();
        assertThat(savedDeviceOpt.get().getUserId()).isEqualTo(testUserId); // WICHTIG: Korrekter Besitzer?


        // --- Teil 3: Befehl an das Gerät senden (als der KORREKTE Benutzer) ---
        Map<String, Object> commandPayload = new HashMap<>();
        commandPayload.put("command", "set_target_temp");
        commandPayload.put("value", 25.0);

        mockMvc.perform(post("/api/v1/devices/{deviceId}/command", newDeviceId)
                        .with(csrf())
                        .with(jwt().jwt(jwt -> jwt.subject(testUserId))) // Simuliert denselben Benutzer
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commandPayload))
                )
                .andExpect(status().isOk());

        // Überprüfe, ob der MQTT-Gateway aufgerufen wurde
        String expectedPayload = objectMapper.writeValueAsString(commandPayload);
        verify(mqttGateway).sendCommand(
                eq(expectedPayload),
                eq("devices/" + newDeviceId + "/commands")
        );


        // --- Teil 4 (Bonus): Befehl an das Gerät senden (als FALSCHER Benutzer) ---
        mockMvc.perform(post("/api/v1/devices/{deviceId}/command", newDeviceId)
                        .with(csrf())
                        .with(jwt().jwt(jwt -> jwt.subject("wrong-user-id-456"))) // Simuliert einen ANDEREN Benutzer
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commandPayload))
                )
                .andExpect(status().isNotFound()); // Erwarte 404 (da der Service "nicht gefunden" zurückgibt)
    }
}
