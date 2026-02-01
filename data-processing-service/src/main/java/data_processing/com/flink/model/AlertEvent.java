package data_processing.com.flink.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {
    private String alertId;
    private String deviceId;
    private AlertType type;
    private String message;
    private double value;
    private Instant timestamp;
}
