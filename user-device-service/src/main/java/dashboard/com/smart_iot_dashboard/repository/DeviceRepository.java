package dashboard.com.smart_iot_dashboard.repository;

import dashboard.com.smart_iot_dashboard.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceIdAndIsActiveTrue(String deviceId);

    Optional<Device> findByDeviceIdAndUserIdAndIsActiveTrue(String deviceId, String userId);

    List<Device> findByUserIdAndIsActiveTrue(String userId);

    Optional<Device> findByDeviceId(String deviceId);

    @Modifying
    @Query("UPDATE Device d SET d.isActive = false, d.deactivatedAt = :deactivatedAt WHERE d.userId = :userId AND d.isActive = true")
    int deactivateDevicesByUserId(@Param("userId") String userId, @Param("deactivatedAt") Instant deactivatedAt);

    List<Device> findByIsActiveFalseAndDeactivatedAtBefore(Instant cutoffTime);
}
