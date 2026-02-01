package dashboard.com.smart_iot_dashboard.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class IncomingAlertDTO {
    private String alertId;
    private String deviceId;
    private String type;
    private String message;
    private Double value;
    private Instant timestamp;
}
