package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;

    @Transactional
    public boolean deleteDeviceByUser(String deviceId, String userId) {
        return deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue(deviceId, userId)
                .map(device -> {
                    device.setActive(false);
                    device.setDeactivatedAt(Instant.now());
                    deviceRepository.save(device);

                    log.info("Device {} marked for deletion by user {}", deviceId, userId);
                    return true;
                })
                .orElse(false);
    }
}
