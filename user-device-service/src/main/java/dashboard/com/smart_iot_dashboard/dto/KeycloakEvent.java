package dashboard.com.smart_iot_dashboard.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakEvent {
    private String type;
    private String realmId;
    private String userId;
    private Long time;
}
