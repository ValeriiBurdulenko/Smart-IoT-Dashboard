package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.dto.DeviceDTO;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final MqttGateway mqttGateway;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public boolean deleteDeviceByUser(String deviceId, String userId) {
        return deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue(deviceId, userId)
                .map(device -> {
                    try {
                        String killCommand = "{\"command\": \"reset_device\", \"value\": 0}";
                        String topic = "devices/" + device.getDeviceId() + "/commands";

                        log.info("Sending KILL command to device: {}", deviceId);

                        mqttGateway.sendCommand(killCommand, topic);

                    } catch (Exception e) {
                        // Protokollieren, aber das Löschen NICHT unterbrechen.
                        // Wenn das Gerät offline ist, wird es trotzdem aus der Datenbank gelöscht.
                        log.warn("Failed to send kill command to device {}: {}", deviceId, e.getMessage());
                    }

                    device.setActive(false);
                    device.setDeactivatedAt(Instant.now());
                    deviceRepository.save(device);

                    clearAuthCache(device.getDeviceId());

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

    private void clearAuthCache(String deviceId) {
        try {
            String authKey = "http:auth:" + deviceId;

            String aclKeyPattern = "http:acl:" + deviceId + ":*";

            log.info("Clearing Redis cache for device {}", deviceId);

            redisTemplate.delete(authKey);

            Set<String> aclKeys = redisTemplate.keys(aclKeyPattern);
            if (aclKeys != null && !aclKeys.isEmpty()) {
                redisTemplate.delete(aclKeys);
                log.debug("Deleted {} ACL cache entries", aclKeys.size());
            }
        } catch (Exception e) {
            // Log, but do not drop the delete transaction.
            // If Redis is unavailable, the device will still be deleted from the database (Postgres),
            // it will simply disconnect from the broker with a 5-minute delay.
            log.error("Failed to clear Redis cache for device {}: {}", deviceId, e.getMessage());
        }
    }
}
