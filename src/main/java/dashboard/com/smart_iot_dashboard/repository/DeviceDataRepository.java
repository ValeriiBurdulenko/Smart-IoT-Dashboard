package dashboard.com.smart_iot_dashboard.repository;

import dashboard.com.smart_iot_dashboard.entity.DeviceData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface DeviceDataRepository extends JpaRepository<DeviceData, Long> {

    // Find all data for a specific device
    List<DeviceData> findByDeviceIdAndDevice_User_Username(Long deviceId, String username);

    // Find data for a device for a specific period of time TODO
    List<DeviceData> findByDeviceIdAndTimestampBetween(Long deviceId, ZonedDateTime start, ZonedDateTime end);
}
