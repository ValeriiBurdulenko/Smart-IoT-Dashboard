package dashboard.com.smart_iot_dashboard.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxDbConfig {

    @Value("${spring.influxdb.url:http://localhost:8086}")
    private String url;

    @Value("${spring.influxdb.token}")
    private String token;

    @Value("${spring.influxdb.org}")
    private String organization;

    @Value("${spring.influxdb.bucket}")
    private String bucket;

    @Bean
    public InfluxDBClient influxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), organization, bucket);
    }
}
