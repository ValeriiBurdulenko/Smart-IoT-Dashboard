package data_processing.com.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryEvent {
    public String deviceId;
    public Instant timestamp;
    public SensorData data;

    public boolean isValid() {

        return deviceId != null && !deviceId.isBlank() &&
                data != null &&
                timestamp != null &&
                data.currentTemperature != null &&
                data.targetTemperature != null;
    }
}