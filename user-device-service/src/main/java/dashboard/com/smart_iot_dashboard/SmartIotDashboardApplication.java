package dashboard.com.smart_iot_dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartIotDashboardApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartIotDashboardApplication.class, args);
	}

}
