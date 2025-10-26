package dashboard.com.smart_iot_dashboard.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
public class MqttIntegrationConfig {

    private static final String MQTT_BROKER_URL = "tcp://192.168.178.23:1883"; // Laptop IP
    // Use ONE client ID for both inbound and outbound for testing
    // private static final String MQTT_CLIENT_ID_INBOUND = "spring-backend-listener";
    // private static final String MQTT_CLIENT_ID_OUTBOUND = "spring-backend-publisher";
    private static final String MQTT_SHARED_CLIENT_ID = "spring-backend-client";
    private static final String TELEMETRY_TOPIC_FILTER = "devices/telemetry";

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] { MQTT_BROKER_URL });
        options.setCleanSession(true); // Standard setting
        factory.setConnectionOptions(options);
        return factory;
    }

    // ========== INBOUND ==========
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inboundChannelAdapter() {
        // Use the SHARED client ID here
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(MQTT_SHARED_CLIENT_ID + "-listener", mqttClientFactory(), TELEMETRY_TOPIC_FILTER);
        // We add '-listener' just to make it unique if truly needed, but it shares the factory
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    // ========== OUTBOUND ==========
    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        // Use the SHARED client ID here as well
        MqttPahoMessageHandler messageHandler =
                new MqttPahoMessageHandler(MQTT_SHARED_CLIENT_ID + "-publisher", mqttClientFactory());
        // Add '-publisher' just in case, shares the factory
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic("default/topic");
        return messageHandler;
    }
}
