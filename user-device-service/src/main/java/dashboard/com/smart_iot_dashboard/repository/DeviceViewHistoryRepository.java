package dashboard.com.smart_iot_dashboard.repository;

import dashboard.com.smart_iot_dashboard.entity.DeviceViewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface DeviceViewHistoryRepository extends JpaRepository<DeviceViewHistory, Long> {

    interface DeviceStatView {
        String getDeviceId();
        String getName();
    }

    @Query(value = """
        SELECT
            d.device_id as deviceId,
            d.name as name
        FROM device_view_history h
        JOIN devices d ON h.device_id = d.device_id
        WHERE h.user_id = :userId
            AND h.viewed_at >= :since
            AND d.is_active = true
        GROUP BY d.device_id, d.name
        ORDER BY COUNT(h.id) DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<DeviceStatView> findTopDevicesSummaries(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since,
            @Param("limit") int limit
    );

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO device_view_history (user_id, device_id, viewed_at)
        SELECT :userId, :deviceId, NOW()
        FROM devices d
        WHERE d.device_id = :deviceId
            AND d.user_id = :userId
            AND d.is_active = true
    """, nativeQuery = true)
    int trackViewIfDeviceExists(
            @Param("userId") String userId,
            @Param("deviceId") String deviceId
    );

    @Query("""
        SELECT COUNT(h) > 0 FROM DeviceViewHistory h
        WHERE h.userId = :userId
            AND h.deviceId = :deviceId
            AND h.viewedAt >= :recentTime
    """)
    boolean hasRecentView(
            @Param("userId") String userId,
            @Param("deviceId") String deviceId,
            @Param("recentTime") LocalDateTime recentTime
    );

    @Modifying
    @Query("DELETE FROM DeviceViewHistory h WHERE h.viewedAt < :olderThan")
    int deleteHistoryOlderThan(@Param("olderThan") LocalDateTime olderThan);
}