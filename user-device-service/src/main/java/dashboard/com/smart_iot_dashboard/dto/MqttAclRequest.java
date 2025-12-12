package dashboard.com.smart_iot_dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MqttAclRequest {
    @NotBlank(message = "Username cannot be empty")
    private String username;
    @NotBlank(message = "Topic cannot be empty")
    private String topic;
    private String clientid; // MQTT Client ID (username)
    @NotNull(message = "Access type (acc) cannot be null")
    private int acc;         // 1 (READ), 2 (WRITE), 4 (SUBSCRIBE)
}
