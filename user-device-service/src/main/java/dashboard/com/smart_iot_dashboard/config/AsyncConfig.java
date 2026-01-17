package dashboard.com.smart_iot_dashboard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Конфигурация для асинхронных операций отслеживания просмотров
 * ✅ Предотвращает переполнение thread pool'а
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Bean(name = "trackingExecutor")
    public Executor trackingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // ✅ Оптимальные параметры для высоконагруженной системы
        executor.setCorePoolSize(10);              // Базовое количество потоков
        executor.setMaxPoolSize(50);               // Максимум потоков
        executor.setQueueCapacity(500);            // Очередь при переполнении
        executor.setThreadNamePrefix("device-tracking-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);   // Ждать завершения до 30 сек
        executor.initialize();

        log.info("Initialized tracking executor: coreSize={}, maxSize={}, queueCapacity={}",
                10, 50, 500);

        return executor;
    }
}