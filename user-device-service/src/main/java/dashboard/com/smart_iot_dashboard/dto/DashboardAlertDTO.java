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
public class DashboardAlertDTO {
    private String alertId;
    private String deviceId;
    private String type;
    private Instant timestamp;
}
