package dashboard.com.smart_iot_dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaimResponse {
    private String deviceId; // The newly generated device ID (e.g., UUID)
    private String deviceToken; // The newly generated raw token (!!! Only returned once !!!)
}
