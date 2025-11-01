package dashboard.com.smart_iot_dashboard.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MqttAclRequest {
    private String username;
    private String topic;
    private String clientid; // MQTT Client ID (username)
    private int acc;         // 1 (READ), 2 (WRITE), 4 (SUBSCRIBE)
}
