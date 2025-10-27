package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.DeviceData;
import dashboard.com.smart_iot_dashboard.repository.DeviceDataRepository;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceDataService {

    private final DeviceDataRepository deviceDataRepository;
    private final DeviceRepository deviceRepository;

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @Transactional
    public DeviceData saveData(Long deviceId, DeviceData data) {
        // Find the device for which the data is intended
        return deviceRepository.findByIdAndUser_Username(deviceId, getCurrentUsername())
                .map(device -> {
                    data.setDevice(device);
                    if (data.getTimestamp() == null) {
                        data.setTimestamp(ZonedDateTime.now());
                    }
                    return deviceDataRepository.save(data);
                }).orElseThrow(() -> new RuntimeException("Device not found with id: " + deviceId));
    }

    @Transactional(readOnly = true)
    public List<DeviceData> findDataByDeviceId(Long deviceId) {
        if (!deviceRepository.existsByIdAndUser_Username(deviceId, getCurrentUsername())) {
            throw new RuntimeException("Device not found with id: " + deviceId);
        }
        return deviceDataRepository.findByDeviceIdAndDevice_User_Username(deviceId, getCurrentUsername());
    }
}
