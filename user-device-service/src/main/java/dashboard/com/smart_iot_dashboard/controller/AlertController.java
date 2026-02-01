package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.AlertDTO;
import dashboard.com.smart_iot_dashboard.dto.ErrorResponse;
import dashboard.com.smart_iot_dashboard.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts API")
@Slf4j
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @Operation(summary = "Get alerts with filtering and pagination")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alerts retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<AlertDTO>> getAlerts(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        log.debug("Fetching alerts for user '{}'", userId);
        return ResponseEntity.ok(alertService.getUserAlerts(userId, unreadOnly, page, size));
    }

    @PatchMapping("/{alertId}/read")
    @Operation(summary = "Mark a specific alert as read")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Alert marked as read"),
            @ApiResponse(responseCode = "404", description = "Alert not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> markAsRead(
            @PathVariable String alertId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        alertService.markAsRead(alertId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all user alerts as read")
    public ResponseEntity<Void> markAsReadAll(
            @AuthenticationPrincipal Jwt jwt
    ) {
        alertService.markAllAsRead(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{alertId}")
    @Operation(summary = "Delete a specific alert")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Alert deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Alert not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteAlert(
            @PathVariable String alertId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        alertService.deleteAlert(alertId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
