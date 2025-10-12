package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.DeviceData;
import dashboard.com.smart_iot_dashboard.repository.DeviceDataRepository;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceDataService {

    private final DeviceDataRepository deviceDataRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public DeviceData saveData(Long deviceId, DeviceData data) {
        // Find the device for which the data is intended
        return deviceRepository.findById(deviceId).map(device -> {
            data.setDevice(device);
            return deviceDataRepository.save(data);
        }).orElseThrow(() -> new RuntimeException("Device not found with id: " + deviceId));
    }

    @Transactional(readOnly = true)
    public List<DeviceData> findDataByDeviceId(Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new RuntimeException("Device not found with id: " + deviceId);
        }
        return deviceDataRepository.findByDeviceId(deviceId);
    }
}
