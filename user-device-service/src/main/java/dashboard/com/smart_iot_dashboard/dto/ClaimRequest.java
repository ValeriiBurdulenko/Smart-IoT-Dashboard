package dashboard.com.smart_iot_dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ClaimRequest {
    @NotBlank(message = "Claim code cannot be blank")
    @Pattern(regexp = "^\\d{3}-\\d{3}$", message = "Claim code must be in format XXX-XXX") // Example format validation
    private String claimCode;
}
