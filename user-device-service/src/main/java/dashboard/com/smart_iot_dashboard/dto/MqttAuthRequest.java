package dashboard.com.smart_iot_dashboard.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MqttAuthRequest {
    // Corresponds to the 'username' sent by the MQTT client (which is our deviceId)
    private String username;
    // Corresponds to the 'password' sent by the MQTT client (which is our deviceToken)
    private String password;
    // TODO The client ID connecting to MQTT (might be useful for logging or specific logic)
    private String clientid;
}
