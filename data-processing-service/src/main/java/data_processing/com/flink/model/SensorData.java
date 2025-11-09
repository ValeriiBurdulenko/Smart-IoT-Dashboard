package data_processing.com.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SensorData {
    public Double currentTemperature;
    public Double targetTemperature;
    public Boolean heatingStatus;
}