package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.DeviceDTO;
import dashboard.com.smart_iot_dashboard.dto.ErrorResponse;
import dashboard.com.smart_iot_dashboard.dto.SetTemperatureRequest;
import dashboard.com.smart_iot_dashboard.dto.UpdateDeviceNameRequest;
import dashboard.com.smart_iot_dashboard.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Device Commands API")
public class DeviceCommandController {

    private final DeviceService deviceService;


    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Delete a device")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Device deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Device not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteDevice(
            @PathVariable String deviceId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        log.info("User '{}' attempting to delete device '{}'", userId, deviceId);

        deviceService.deleteDeviceByUser(deviceId, userId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get all active devices for current user")
    public ResponseEntity<List<DeviceDTO>> getDevicesForCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(deviceService.findAllDevicesByUserIdAndIsActiveTrue(jwt.getSubject()));
    }

    @GetMapping("/{deviceId}")
    @Operation(summary = "Get device details by ID")
    public ResponseEntity<DeviceDTO> getDeviceById(
            @PathVariable String deviceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(deviceService.findDeviceByIdAndUserId(deviceId, jwt.getSubject()));
    }

    @PatchMapping("/{deviceId}")
    @Operation(summary = "Update device display name")
    public ResponseEntity<DeviceDTO> updateDeviceName(
            @PathVariable String deviceId,
            @Valid @RequestBody UpdateDeviceNameRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("Rename request: user={}, device={}, name={}", jwt.getSubject(), deviceId, request.getName());

        DeviceDTO updatedDevice = deviceService.updateDeviceName(deviceId, jwt.getSubject(), request.getName());
        return ResponseEntity.ok(updatedDevice);
    }

    @PostMapping("/{deviceId}/command/temperature")
    @Operation(summary = "Set target temperature for device")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Command accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid temperature value",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "MQTT broker unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> setTemperature(
            @PathVariable String deviceId,
            @Valid @RequestBody SetTemperatureRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("Temp command: user={}, device={}, value={}", jwt.getSubject(), deviceId, request.getValue());


        deviceService.updateTargetTemperature(deviceId, jwt.getSubject(), request.getValue());

        return ResponseEntity.accepted().build();
    }
}
