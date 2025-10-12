package dashboard.com.smart_iot_dashboard.repository;

import dashboard.com.smart_iot_dashboard.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    // Method for finding all devices belonging to a specific user
    List<Device> findByUserId(Long userId);

    // Method for searching devices by status
    List<Device> findByStatus(String status);
}
