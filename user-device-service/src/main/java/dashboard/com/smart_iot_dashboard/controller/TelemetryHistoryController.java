package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.ErrorResponse;
import dashboard.com.smart_iot_dashboard.service.DeviceService;
import dashboard.com.smart_iot_dashboard.service.TelemetryHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Telemetry API", description = "Access to historical device data")
public class TelemetryHistoryController {

    private final TelemetryHistoryService telemetryService;
    private final DeviceService deviceService;

    @GetMapping("/{deviceId}/telemetry/history")
    @Operation(summary = "Get aggregated temperature history")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved history"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied to device",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Device not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "InfluxDB unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<TelemetryHistoryService.TelemetryHistoryPoint>> getHistory(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "-1h") String range,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();

        // Ownership
        deviceService.verifyDeviceOwnership(deviceId, userId);

        // Data request
        return ResponseEntity.ok(telemetryService.getTelemetryHistory(deviceId, range));
    }
}
