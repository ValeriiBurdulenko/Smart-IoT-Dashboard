package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.dto.DeviceDTO;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

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

    @Transactional(readOnly = true) // readOnly = true ist eine gute Optimierung für GET
    public List<DeviceDTO> findAllDevicesByUserIdAndIsActiveTrue(String userId) {

        // 1. Hole die Entities aus der DB
        List<Device> devices = deviceRepository.findByUserIdAndIsActiveTrue(userId);

        // 2. Konvertiere die Liste von Entities in eine Liste von DTOs
        return devices.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private DeviceDTO convertToDTO(Device device) {
        DeviceDTO dto = new DeviceDTO();
        dto.setDeviceId(device.getDeviceId());
        dto.setName(device.getName());
        dto.setLocation(device.getLocation());
        dto.setActive(device.isActive());
        dto.setDeactivatedAt(device.getDeactivatedAt());
        // Das Feld 'hashedDeviceToken' wird ignoriert und nie kopiert.
        return dto;
    }
}
