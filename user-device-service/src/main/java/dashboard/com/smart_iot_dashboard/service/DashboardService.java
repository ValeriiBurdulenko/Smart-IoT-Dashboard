package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.dto.DashboardStats;
import dashboard.com.smart_iot_dashboard.dto.DeviceSummaryDTO;
import dashboard.com.smart_iot_dashboard.repository.DeviceViewHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

final class DashboardConstants {
    private DashboardConstants() {}

    static final int TOP_DEVICES_LIMIT = 5;
    static final int HISTORY_DAYS = 30;
    static final int RECENT_VIEW_MINUTES = 1;
}

@Service
@Slf4j
public class DashboardService {

    private final DeviceViewHistoryRepository historyRepository;
    private final DashboardService self;


    public DashboardService(DeviceViewHistoryRepository historyRepository,
                            @Lazy DashboardService self) {
        this.historyRepository = historyRepository;
        this.self = self;
    }


    @Transactional(readOnly = true)
    public DashboardStats getStats(String userId) {
        return self.getStats(userId, LocalDateTime.now().minusDays(DashboardConstants.HISTORY_DAYS));
    }

    /**
     * Receives dashboard statistics with a custom date
     */
    @Transactional(readOnly = true)
    public DashboardStats getStats(String userId, LocalDateTime from) {
        DashboardStats stats = new DashboardStats();

        if (isInvalidUserId(userId)) {
            log.warn("Invalid userId: {}", userId);
            stats.setPopularDevices(Collections.emptyList());
            return stats;
        }

        try {
            List<DeviceViewHistoryRepository.DeviceStatView> views = historyRepository.findTopDevicesSummaries(
                    userId,
                    from,
                    DashboardConstants.TOP_DEVICES_LIMIT
            );

            List<DeviceSummaryDTO> summaries = views.stream()
                    .map(view -> DeviceSummaryDTO.builder()
                            .deviceId(view.getDeviceId())
                            .name(view.getName())
                            .build())
                    .toList();

            if (summaries.isEmpty()) {
                log.debug("No device history found for user: {}", userId);
            } else {
                log.debug("Found {} popular devices for user {}", summaries.size(), userId);
            }

            stats.setPopularDevices(summaries);
        } catch (Exception e) {
            log.error("Failed to get dashboard stats for user: {}", userId, e);
            stats.setPopularDevices(Collections.emptyList());
        }

        return stats;
    }

    /**
     * Tracks device viewing
     * Asynchronously, with error handling and spam protection
     */
    @Async("trackingExecutor")
    @Transactional
    public void trackDeviceView(String userId, String deviceId) {
        // Валидация
        if (isInvalidUserId(userId) || isInvalidDeviceId(deviceId)) {
            log.warn("Invalid track request: userId={}, deviceId={}", userId, deviceId);
            return;
        }

        try {
            // Spam protection: checking whether there was a last-minute view
            LocalDateTime recentTime = LocalDateTime.now()
                    .minusMinutes(DashboardConstants.RECENT_VIEW_MINUTES);

            if (historyRepository.hasRecentView(userId, deviceId, recentTime)) {
                log.debug("Recent view already exists: user={}, device={}", userId, deviceId);
                return;
            }

            int inserted = historyRepository.trackViewIfDeviceExists(userId, deviceId);

            if (inserted > 0) {
                log.debug("Successfully tracked view: user={}, device={}", userId, deviceId);
            } else {
                log.warn("Device not found or inactive: user={}, device={}", userId, deviceId);
            }
        } catch (Exception e) {
            log.error("Failed to track device view: user={}, device={}", userId, deviceId, e);
            // Can be sent to the queue for retry (RabbitMQ, Kafka, etc.)
        }
    }

    /**
     * Cleans up old records (called on a schedule)
     */
    @Transactional
    public void cleanupOldHistory(int daysToKeep) {
        try {
            LocalDateTime olderThan = LocalDateTime.now().minusDays(daysToKeep);
            int deleted = historyRepository.deleteHistoryOlderThan(olderThan);
            log.info("Cleaned up {} old history records", deleted);
        } catch (Exception e) {
            log.error("Failed to cleanup history", e);
        }
    }

    // ------------ Validation helpers ------------
    private boolean isInvalidUserId(String userId) {
        return userId == null || userId.isBlank() || userId.length() > 100;
    }

    private boolean isInvalidDeviceId(String deviceId) {
        return deviceId == null || deviceId.isBlank() || deviceId.length() > 100;
    }
}