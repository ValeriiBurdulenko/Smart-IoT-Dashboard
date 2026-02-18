package dashboard.com.smart_iot_dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDTO {
    private String alertId;

    private String deviceId;
    private String type;
    private String message;
    private Double value;
    private Instant timestamp;
    private boolean isRead;
}
