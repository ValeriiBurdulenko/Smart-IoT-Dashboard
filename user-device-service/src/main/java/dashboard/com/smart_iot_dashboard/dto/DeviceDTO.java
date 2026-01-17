package dashboard.com.smart_iot_dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceDTO {

    //!!!!Frontend GET Response
    private String deviceId;
    private String name;
    private String location;
    private boolean isActive;
    private Instant deactivatedAt;
    private Double targetTemperature;

    // ❌ HINWEIS: Das Feld 'hashedDeviceToken' fehlt hier absichtlich!
}
