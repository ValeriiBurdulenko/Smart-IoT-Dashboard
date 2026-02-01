package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.DashboardStats;
import dashboard.com.smart_iot_dashboard.dto.ErrorResponse;
import dashboard.com.smart_iot_dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dashboard API", description = "Aggregated user statistics")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Get user dashboard statistics")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dashboard stats retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DashboardStats.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - user not authenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Service Unavailable - Database error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DashboardStats> getDashboardStats(
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        log.debug("Dashboard stats requested for user: {}", userId);
        DashboardStats stats = dashboardService.getStats(userId);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/track/{deviceId}")
    @Operation(summary = "Track device view event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "View tracking accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid device ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> trackView(
            @PathVariable @NotBlank(message = "Device ID cannot be blank") String deviceId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        log.debug("Tracking device view: user={}, device={}", userId, deviceId);

        dashboardService.trackDeviceView(userId, deviceId);

        return ResponseEntity.accepted().build();
    }
}
