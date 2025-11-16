package dashboard.com.smart_iot_dashboard.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
public class DeviceDTO {

    //!!!!Frontend GET Response
    private String deviceId;
    private String name;
    private String location;
    private boolean isActive;
    private Instant deactivatedAt;

    // ❌ HINWEIS: Das Feld 'hashedDeviceToken' fehlt hier absichtlich!
}
