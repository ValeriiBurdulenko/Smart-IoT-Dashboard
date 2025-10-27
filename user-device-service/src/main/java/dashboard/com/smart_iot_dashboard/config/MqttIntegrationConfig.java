package dashboard.com.smart_iot_dashboard.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
public class MqttIntegrationConfig {

    @Value("${mqtt.broker.url}")
    private String mqttBrokerUrl;

    @Value("${mqtt.client.id.outbound}")
    private String mqttClientIdOutbound;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] { mqttBrokerUrl });
        // Add username/password if Mosquitto requires it via go-auth
        // options.setUserName("backendUsername"); // This needs to be checked by go-auth
        // options.setPassword("backendPassword".toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10); // seconds
        options.setKeepAliveInterval(60); // seconds
        factory.setConnectionOptions(options);
        return factory;
    }

    // --- OUTBOUND Flow ---

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutboundHandler() {
        MqttPahoMessageHandler messageHandler =
                new MqttPahoMessageHandler(mqttClientIdOutbound + "-" + System.currentTimeMillis(), mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultQos(1);
        return messageHandler;
    }

    // --- (Optional) INBOUND Flow (if backend needs to listen too) ---
    /*
    @Bean public MessageChannel mqttInputChannel() { ... }
    @Bean public MessageProducer inboundChannelAdapter() { ... }
    */
}
