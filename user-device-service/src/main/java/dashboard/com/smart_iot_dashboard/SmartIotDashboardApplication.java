package dashboard.com.smart_iot_dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SmartIotDashboardApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartIotDashboardApplication.class, args);
	}

}
