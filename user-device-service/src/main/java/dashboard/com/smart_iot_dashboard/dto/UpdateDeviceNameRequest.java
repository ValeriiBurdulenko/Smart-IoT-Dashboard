package dashboard.com.smart_iot_dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDeviceNameRequest {

    @NotBlank(message = "Device name cannot be empty")
    @Size(min = 1, max = 50, message = "Device name must be between 1 and 50 characters")
    private String name;
}
