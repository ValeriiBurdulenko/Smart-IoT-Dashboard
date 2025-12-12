package dashboard.com.smart_iot_dashboard.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

@Configuration
public class MqttIntegrationConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttIntegrationConfig.class);

    @Value("${mqtt.broker.url.tls}")
    private String mqttBrokerUrlTls;

    @Value("${mqtt.client.id.outbound}")
    private String mqttClientIdOutbound;

    @Value("${mqtt.bridge.username}")
    private String bridgeUsername;

    @Value("${mqtt.bridge.password}")
    private String bridgePassword;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] { mqttBrokerUrlTls });


        options.setUserName(bridgeUsername);
        options.setPassword(bridgePassword.toCharArray());

        // --- TLS Configuration ---
        try (InputStream caInput = new ClassPathResource("ca.crt").getInputStream()) {
            // 1. Download our CA certificate from the resources folder
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate ca = (X509Certificate) cf.generateCertificate(caInput);

            // 2. Creating a trusted certificate store (TrustStore) in memory
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("my-ca", ca);

            // 3. Create a TrustManager that trusts certificates from our TrustStore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            // 4. Create an SSLContext using our TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, tmf.getTrustManagers(), null);

            // 5. Set up the socket factory in the MQTT connection options
            options.setSocketFactory(sslContext.getSocketFactory());
            log.info("✅ Successfully configured TLS for MQTT connection.");

        } catch (Exception e) {
            log.error("❌ Failed to configure TLS for MQTT connection!", e);
            throw new RuntimeException("Failed to configure MQTT TLS", e);
        }

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
        messageHandler.setAsync(false);
        messageHandler.setDefaultQos(1);
        return messageHandler;
    }

    // --- (Optional) INBOUND Flow (if backend needs to listen too) ---
    /*
    @Bean public MessageChannel mqttInputChannel() { ... }
    @Bean public MessageProducer inboundChannelAdapter() { ... }
    */
}
