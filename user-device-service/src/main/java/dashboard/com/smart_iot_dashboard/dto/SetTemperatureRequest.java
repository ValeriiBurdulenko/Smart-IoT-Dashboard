package dashboard.com.smart_iot_dashboard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetTemperatureRequest {

    @NotNull(message = "Temperature value is required")
    @Min(value = -40, message = "Temperature cannot be below -40°C")
    @Max(value = 100, message = "Temperature cannot exceed 100°C")
    private Double value;
}
