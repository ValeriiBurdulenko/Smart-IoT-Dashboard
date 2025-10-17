package dashboard.com.smart_iot_dashboard.repository;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    // Method for finding all devices belonging to a specific user
    List<Device> findByUser_Username(String username);

    Optional<Device> findByIdAndUser_Username(Long id, String username);

    default boolean existsByIdAndUser_Username(Long id, String username) {
        return false;
    }

    // Method for searching devices by status
    List<Device> findByStatus(String status);

    String user(User user);
}
