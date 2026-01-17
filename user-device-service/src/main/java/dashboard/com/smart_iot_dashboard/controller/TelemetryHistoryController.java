package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.service.DeviceService;
import dashboard.com.smart_iot_dashboard.service.TelemetryHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Telemetry API", description = "Access to historical device data")
public class TelemetryHistoryController {

    private final TelemetryHistoryService telemetryService;
    private final DeviceService deviceService;

    @GetMapping("/{deviceId}/telemetry/history")
    @Operation(summary = "Get aggregated temperature history")
    public ResponseEntity<List<TelemetryHistoryService.TelemetryHistoryPoint>> getHistory(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "-1h") String range,
            Principal principal
    ) {
        // 1. Authorisation check (Principal must not be null)
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        // 2. =Ownership
        if (!deviceService.isDeviceOwner(deviceId, principal.getName())) {
            log.warn("Access denied: User {} tried to access history of device {}", principal.getName(), deviceId);
            return ResponseEntity.status(403).build();
        }

        // 3. Data request
        return ResponseEntity.ok(telemetryService.getTelemetryHistory(deviceId, range));
    }
}
