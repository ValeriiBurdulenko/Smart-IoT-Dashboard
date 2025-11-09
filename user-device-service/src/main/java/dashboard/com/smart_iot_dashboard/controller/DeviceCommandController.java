package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import dashboard.com.smart_iot_dashboard.service.DeviceService;
import dashboard.com.smart_iot_dashboard.service.MqttGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceCommandController {

    private final DeviceRepository deviceRepository;
    private final MqttGateway mqttGateway;
    private final DeviceService deviceService;

    @PostMapping("/{deviceId}/command")
    public ResponseEntity<Void> sendCommandToDevice(
            @PathVariable String deviceId,
            @RequestBody String commandPayload,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        log.info("User '{}' attempting to send command to device '{}'", userId, deviceId);

        return deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue(deviceId, userId)
                .map(device -> {
                    String commandTopic = "devices/" + device.getDeviceId() + "/commands";

                    try {
                        log.debug("Sending command to topic '{}': {}", commandTopic, commandPayload);
                        mqttGateway.sendCommand(commandPayload, commandTopic);
                        log.info("Command successfully sent for device '{}'", deviceId);
                        return ResponseEntity.ok().<Void>build();
                    } catch (Exception e) {
                        log.error("Failed to send MQTT command for device '{}'", deviceId, e);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send command via MQTT");
                    }
                })
                .orElseThrow(() -> {
                    log.warn("Device '{}' not found or not owned by user '{}'", deviceId, userId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found or access denied");
                });
    }

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
}
