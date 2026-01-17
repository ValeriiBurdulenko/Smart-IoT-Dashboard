package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.DeviceDTO;
import dashboard.com.smart_iot_dashboard.exception.DeviceNotFoundException;
import dashboard.com.smart_iot_dashboard.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceCommandController {

    private final DeviceService deviceService;

    private static final double MIN_TEMP = -40.0;
    private static final double MAX_TEMP = 100.0;

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> deleteDevice(
            @PathVariable String deviceId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        log.info("User '{}' attempting to delete device '{}'", userId, deviceId);

        boolean deleted = deviceService.deleteDeviceByUser(deviceId, userId);

        if (deleted) {
            log.info("Device '{}' deleted successfully by user '{}'", deviceId, userId);
            return ResponseEntity.noContent().build(); // 204 No Content
        } else {
            log.warn("Device '{}' not found or not owned by user '{}'", deviceId, userId);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<DeviceDTO>> getDevicesForCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();

        log.debug("Fetching devices for user '{}'", userId);
        List<DeviceDTO> devices = deviceService.findAllDevicesByUserIdAndIsActiveTrue(userId);

        return ResponseEntity.ok(devices);
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceDTO> getDeviceById(
            @PathVariable String deviceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();

        log.debug("Fetching device {} for user '{}'", deviceId, userId);

        DeviceDTO device = deviceService.findDeviceByIdAndUserId(deviceId, userId);

        return ResponseEntity.ok(device);
    }

    @PatchMapping("/{deviceId}")
    public ResponseEntity<DeviceDTO> updateDeviceName(
            @PathVariable String deviceId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        String newName = body.get("name");

        if (newName == null || newName.isBlank()) {
            log.warn("Bad Request: User '{}' tried to set empty name for device '{}'", userId, deviceId);
            return ResponseEntity.badRequest().build();
        }

        if (newName.length() > 50) {
            log.warn("Bad Request: Name too long for device '{}'", deviceId);
            return ResponseEntity.badRequest().build();
        }

        log.info("Request: User '{}' renaming device '{}' to '{}'", userId, deviceId, newName);

        try {
            DeviceDTO updatedDevice = deviceService.updateDeviceName(deviceId, userId, newName);
            return ResponseEntity.ok(updatedDevice);
        } catch (RuntimeException e) {
            log.error("Failed to update name for device '{}': {}", deviceId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{deviceId}/command/temperature")
    public ResponseEntity<Void> setTemperature(
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        Object valueObj = body.get("value");

        if (valueObj == null) {
            log.warn("Bad Request: Missing 'value' in temp command for device '{}'", deviceId);
            return ResponseEntity.badRequest().build();
        }

        try {
            Double value = Double.valueOf(valueObj.toString());
            if (value < MIN_TEMP || value > MAX_TEMP) {
                log.warn("Security Alert: User '{}' tried to set invalid temp '{}' for device '{}'", userId, value, deviceId);
                return ResponseEntity.badRequest().build();
            }
            log.info("Request: User '{}' setting temp '{}' for device '{}'", userId, value, deviceId);
            deviceService.updateTargetTemperature(deviceId, userId, value);
            return ResponseEntity.ok().build();
        } catch (NumberFormatException e) {
            log.warn("Bad Request: Invalid temp value '{}' from user '{}'", valueObj, userId);
            return ResponseEntity.badRequest().build();
        } catch (DeviceNotFoundException e) {
            log.warn("Device not found: {}", deviceId);
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            log.error("Failed to set temperature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
