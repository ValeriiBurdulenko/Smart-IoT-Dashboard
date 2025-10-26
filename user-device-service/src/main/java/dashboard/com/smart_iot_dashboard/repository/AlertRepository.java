package dashboard.com.smart_iot_dashboard.repository;

import dashboard.com.smart_iot_dashboard.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // Find all alerts for a specific device
    List<Alert> findByDeviceIdAndDevice_User_Username(Long deviceId, String username);

    Optional<Alert> findByIdAndDevice_User_Username(Long alertId, String username);
}
