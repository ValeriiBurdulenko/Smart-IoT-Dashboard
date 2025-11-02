package dashboard.com.smart_iot_dashboard.service;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@MessagingGateway(defaultRequestChannel = "mqttOutboundChannel") // Sends messages to this channel
public interface MqttGateway {

    void sendCommand(String payload, @Header(MqttHeaders.TOPIC) String topic);
}
