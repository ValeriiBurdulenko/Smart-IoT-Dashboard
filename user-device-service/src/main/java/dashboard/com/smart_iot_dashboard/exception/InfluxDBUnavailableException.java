package dashboard.com.smart_iot_dashboard.exception;

public class InfluxDBUnavailableException extends ServiceUnavailableException {
    public InfluxDBUnavailableException() {
        super("InfluxDB is temporarily unavailable", "INFLUXDB_UNAVAILABLE");
    }
}
