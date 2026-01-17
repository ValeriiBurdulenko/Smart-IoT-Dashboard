package dashboard.com.smart_iot_dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryBridgeService {

    private final SimpMessagingTemplate messagingTemplate;


    @KafkaListener(
            topics = "${kafka.topic.processed:iot-telemetry-processed}",
            groupId = "${kafka.consumer.group-id:backend-group}"
    )
    public void forwardToWebSocket(ConsumerRecord<String, String> recordProcessed) {
        String deviceId = recordProcessed.key();
        String payload = recordProcessed.value();

        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("Skipping message without Key (deviceId). Offset: {}", recordProcessed.offset());
            return;
        }

        try {
            // Spring send it to RabbitMQ (Exchange: amq.topic, Routing Key: device.{id})
            String destination = "/topic/device." + deviceId;
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.error("Failed to forward message to WS for device {}: {}", deviceId, e.getMessage());
            //TODO DLQ
        }
    }
}
