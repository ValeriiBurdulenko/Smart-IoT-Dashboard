package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataRetentionService {

    private final DeviceRepository deviceRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.deletions}")
    private String deleteTopic;

    @Value("${retention.purge.days}")
    private long retentionDays;

    /**
     * Starts every day at 3:00 a.m.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void purgeExpiredDevices() {
        log.info("[DataRetentionJob] Starting data cleanup task...");

        Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        List<Device> expiredDevices = deviceRepository.findByIsActiveFalseAndDeactivatedAtBefore(cutoffTime);

        if (expiredDevices.isEmpty()) {
            log.info("[DataRetentionJob] No devices for final deletion found.");
            return;
        }

        log.warn("[DataRetentionJob]  {} devices found for final cleaning.", expiredDevices.size());

        for (Device device : expiredDevices) {
            String deviceId = device.getDeviceId();
            try {
                // 2. Create a message for Flink to clear InfluxDB
                // (Use JSON so that additional information can be added in the future)
                Map<String, String> deleteEvent = Map.of("deviceId", deviceId, "action", "PURGE");
                String payload = objectMapper.writeValueAsString(deleteEvent);

                // 3. Send the event to Kafka
                // We use deviceId as a key so that all deletions for a single
                // device go to one partition (important for Flink)
                kafkaTemplate.send(deleteTopic, deviceId, payload);

                log.info("[DataRetentionJob] PURGE event for {} sent to Kafka.", deviceId);

                // 4. Permanently remove the device from PostgreSQL
                deviceRepository.delete(device);
                log.info("[DataRetentionJob] The device {} has been removed from PostgreSQL.", deviceId);

            } catch (Exception e) {
                log.error("[DataRetentionJob] Error processing delete for {}: {}", deviceId, e.getMessage());
                // We do not roll back the transaction to continue with other devices,
                // this device will be processed next time.
            }
        }
    }
}
