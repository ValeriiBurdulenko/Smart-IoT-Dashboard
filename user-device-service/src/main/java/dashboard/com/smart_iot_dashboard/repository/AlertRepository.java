package dashboard.com.smart_iot_dashboard.repository;

import dashboard.com.smart_iot_dashboard.dto.DashboardAlertDTO;
import dashboard.com.smart_iot_dashboard.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Page<Alert> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    Page<Alert> findByUserIdAndIsReadFalseOrderByTimestampDesc(String userId, Pageable pageable);

    Optional<Alert> findByAlertIdAndUserId(String alertId, String userId);

    boolean existsByAlertId(String alertId);

    @Modifying
    @Transactional
    @Query("UPDATE Alert a SET a.isRead = true WHERE a.userId = :userId")
    void markAllAsRead(@Param("userId") String userId);

    @Query("""
        SELECT new dashboard.com.smart_iot_dashboard.dto.DashboardAlertDTO(
            a.alertId,
            a.deviceId,
            a.type,
            a.timestamp
        )
        FROM Alert a
        WHERE a.userId = :userId
        ORDER BY a.timestamp DESC
    """)
    List<DashboardAlertDTO> findRecentAlert(@Param("userId") String userId,  Pageable pageable);
}
