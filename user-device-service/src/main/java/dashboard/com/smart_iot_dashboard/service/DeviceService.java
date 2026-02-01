package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.dto.DeviceDTO;
import dashboard.com.smart_iot_dashboard.exception.*;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final MqttGateway mqttGateway;
    private final StringRedisTemplate redisTemplate;

    private static final double MIN_TEMP = -40.0;
    private static final double MAX_TEMP = 100.0;

    @Transactional
    public void deleteDeviceByUser(String deviceId, String userId) {
        Device device = deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue(deviceId, userId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        try {
            String confirmCode = device.getDeviceId().substring(0, 8);
            sendMqttCommand(device.getDeviceId(), "reset_device", confirmCode, 1, false);
        } catch (MqttBrokerUnavailableException e) {
            // Log, but do NOT interrupt deletion.
            // If the device is offline, it will still be deleted from the database.
            log.warn("Kill switch failed for device {} (likely offline). Proceeding with DB deletion.", deviceId);
        }

        device.setActive(false);
        device.setDeactivatedAt(Instant.now());
        deviceRepository.save(device);

        clearAuthCache(device.getDeviceId());
        log.info("Device {} marked for deletion by user {}", deviceId, userId);
    }

    @Transactional
    public void updateTargetTemperature(String deviceId, String userId, Double newTemp) {
        if (newTemp < MIN_TEMP || newTemp > MAX_TEMP) {
            throw new InvalidTemperatureException("Temperature out of range", MIN_TEMP, MAX_TEMP);
        }

        Device device = deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue(deviceId, userId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        device.setTargetTemperature(newTemp);
        deviceRepository.save(device);

        log.info("Device {} target temp updated to {} in DB", deviceId, newTemp);

        sendMqttCommand(device.getDeviceId(), "set_target_temp", newTemp, 1, true);
    }

    @Transactional(readOnly = true)
    public List<DeviceDTO> findAllDevicesByUserIdAndIsActiveTrue(String userId) {
        return deviceRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceDTO findDeviceByIdAndUserId(String deviceId, String userId) {
        return deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue(deviceId, userId)
                .map(this::convertToDTO)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
    }

    @Transactional
    public DeviceDTO updateDeviceName(String deviceId, String userId, String newName) {
        if (newName == null || newName.isBlank() || newName.length() > 50) {
            throw new ValidationException("Invalid device name", "INVALID_NAME");
        }

        Device device = deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue(deviceId, userId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        device.setName(newName);
        Device saved = deviceRepository.save(device);
        log.info("Device {} name updated to '{}'", deviceId, newName);
        return convertToDTO(saved);
    }

    public void verifyDeviceOwnership(String deviceId, String userId) {
        if (!deviceRepository.existsByDeviceIdAndUserIdAndIsActiveTrue(deviceId, userId)) {
            log.warn("Access Denied: User {} is not the owner of device {}", userId, deviceId);
            if (deviceRepository.existsByDeviceIdAndIsActiveTrue(deviceId)) {
                throw AccessDeniedException.forResource(deviceId);
            } else {
                throw new DeviceNotFoundException(deviceId);
            }
        }
    }

    private DeviceDTO convertToDTO(Device device) {
        return DeviceDTO.builder()
                .deviceId(device.getDeviceId())
                .name(device.getName())
                .location(device.getLocation())
                .isActive(device.isActive())
                .deactivatedAt(device.getDeactivatedAt())
                .targetTemperature(device.getTargetTemperature())
                .build();
        // Das Feld 'hashedDeviceToken' wird ignoriert und nie kopiert.
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

    private void sendMqttCommand(String deviceId, String commandName, Object value, int qos, boolean retained) {
        try {
            String payload;

            if (value instanceof String) {
                payload = String.format("{\"command\": \"%s\", \"value\": \"%s\"}", commandName, value);
            }
            else {
                payload = String.format("{\"command\": \"%s\", \"value\": %s}", commandName, value);
            }

            String topic = "devices/" + deviceId + "/commands";

            log.info("Sending MQTT command '{}' to {}. Retained: {}", commandName, deviceId, retained);

            mqttGateway.sendCommand(payload, topic, qos, retained);

        } catch (Exception e) {
            log.warn("Failed to send MQTT command '{}' to device {}: {}", commandName, deviceId, e.getMessage());
            throw new MqttBrokerUnavailableException("Cannot reach MQTT broker to send command");
        }
    }
}
