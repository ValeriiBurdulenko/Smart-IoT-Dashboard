package dashboard.com.smart_iot_dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private Instant timestamp;

    private int status;

    private String error;

    private String errorCode; // for React

    private String message; // for Logs and User

    private Object details;

    private String traceId;

    private String path;
}