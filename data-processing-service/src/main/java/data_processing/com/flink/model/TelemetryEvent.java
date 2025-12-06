package data_processing.com.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryEvent {
    private String deviceId;
    private Instant timestamp;
    private SensorData data;

    @JsonIgnore
    public boolean isValid() {
        if (deviceId == null || deviceId.isBlank() ||
                timestamp == null ||
                data == null
                || data.getCurrentTemperature() == null) {
            return false;
        }

        //Security Check: Null Byte Injection
        if (deviceId.indexOf('\0') >= 0) {
            return false;
        }

        return true;
    }
}