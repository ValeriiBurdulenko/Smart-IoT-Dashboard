package dashboard.com.smart_iot_dashboard.dto;

import lombok.Data;
@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String role;
}
