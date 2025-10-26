package dashboard.com.smart_iot_dashboard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MqttBridgeService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMqttMessage(String payload) {
        System.out.println("📡 MQTT message received: " + payload);

        kafkaTemplate.send("iot-telemetry-raw", payload);

        System.out.println("✅ Message redirected to Kafka topic 'iot-telemetry-raw'");
    }
}
