package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertConsumer {

    private final AlertService alertService;
    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "${app.kafka.topic.alerts:iot-telemetry-alerts}", groupId = "backend-group")
    public void consumeAlert(ConsumerRecord<String, String> recordAlert) {
        String payload = recordAlert.value();
        String deviceId = recordAlert.key();

        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("Skipping message without Key (deviceId). Offset: {}", recordAlert.offset());
            return;
        }

        log.info("Received ALERT: {}", payload);

        // 1. Save in DB
        Alert savedAlert = alertService.processIncomingAlert(payload);

        if(savedAlert != null) {
            try {
                // Spring send it to RabbitMQ (Exchange: amq.topic, Routing Key: device.{id})
                String destination = "/topic/device." + deviceId + ".alert";
                messagingTemplate.convertAndSend(destination, payload);

                String userTopic = "/topic/user." + savedAlert.getUserId() + ".alerts";
                messagingTemplate.convertAndSend(userTopic, payload);

            } catch (Exception e) {
                log.error("Failed to forward message to WS for device {}: {}", deviceId, e.getMessage());
                //TODO DLQ
            }
        }
    }
}
