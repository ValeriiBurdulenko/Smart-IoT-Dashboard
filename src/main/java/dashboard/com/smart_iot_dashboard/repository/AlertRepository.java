package dashboard.com.smart_iot_dashboard.repository;

import dashboard.com.smart_iot_dashboard.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // Find all alerts for a specific device
    List<Alert> findByDeviceId(Long deviceId);

    // Find all unconfirmed alerts for the device
    List<Alert> findByDeviceIdAndAcknowledgedFalse(Long deviceId);
}
