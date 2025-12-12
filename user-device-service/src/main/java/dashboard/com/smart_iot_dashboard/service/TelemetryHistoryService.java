package dashboard.com.smart_iot_dashboard.service;


import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TelemetryHistoryService {

    private final InfluxDBClient influxDBClient;

    @Value("${spring.influxdb.org}")
    private String organization;

    @Value("${spring.influxdb.bucket}")
    private String bucket;

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F-]{36}$");

    public TelemetryHistoryService(InfluxDBClient influxDBClient) {
        this.influxDBClient = influxDBClient;
    }

    @Data
    public static class TelemetryHistoryPoint{
        private Instant timestamp;
        private Double temperature;
    }

    public List<TelemetryHistoryPoint> getTelemetryHistory(String deviceId, String range){
        if (!isValidDeviceId(deviceId)) {
            log.warn("Suspicious deviceId format: {}", deviceId);
            return new ArrayList<>();
        }

        if (!isValidRange(range)) {
            log.warn("Invalid range requested: {}", range);
            range = "-1h";
        }

        String window = switch (range) {
            case "-24h" -> "15m";
            case "-6h" -> "5m";
            default -> "1m";
        };

        String query = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: %s) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"telemetry\") " +
                        "|> filter(fn: (r) => r[\"deviceId\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"currentTemperature\") " +
                        "|> aggregateWindow(every: %s, fn: mean, createEmpty: false) " +
                        "|> yield(name: \"mean\")",
                bucket, range, deviceId, window
        );

        List<TelemetryHistoryPoint> points = new ArrayList<>();

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(query, organization);

            for (FluxTable table : tables) {
                for (FluxRecord fluxRecord : table.getRecords()) {
                    TelemetryHistoryPoint point = new TelemetryHistoryPoint();
                    point.setTimestamp(fluxRecord.getTime());
                    if (fluxRecord.getValue() instanceof Number numberFluxRecord) {
                        double val = numberFluxRecord.doubleValue();
                        point.setTemperature(Math.round(val * 100.0) / 100.0);
                        points.add(point);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error querying InfluxDB for {}: {}", deviceId, e.getMessage());
            // We do not throw an exception so as not to break the front end with a 500 error
        }

        return points;
    }

    private boolean isValidDeviceId(String id) {
        return id != null && UUID_PATTERN.matcher(id).matches();
    }

    private boolean isValidRange(String range) {
        return range != null && (range.equals("-1h") || range.equals("-6h") || range.equals("-24h"));
    }
}
