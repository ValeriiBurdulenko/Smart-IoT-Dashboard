package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    @Transactional
    public boolean deleteDeviceByUser(String deviceId, String userId) {
        return deviceRepository.findByDeviceIdAndUserId(deviceId, userId)
                .map(device -> {
                    deviceRepository.delete(device);
                    return true;
                })
                .orElse(false);
    }
}
