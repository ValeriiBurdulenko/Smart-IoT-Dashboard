package dashboard.com.smart_iot_dashboard.exception;

public class MqttBrokerUnavailableException extends ServiceUnavailableException {
    public MqttBrokerUnavailableException() {
        super("MQTT broker is temporarily unavailable", "MQTT_BROKER_UNAVAILABLE");
    }

    public MqttBrokerUnavailableException(String message) {
        super(message, "MQTT_BROKER_UNAVAILABLE");
    }
}
