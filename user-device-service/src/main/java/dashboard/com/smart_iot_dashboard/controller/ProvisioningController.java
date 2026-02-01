package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.ClaimRequest;
import dashboard.com.smart_iot_dashboard.dto.ClaimResponse;
import dashboard.com.smart_iot_dashboard.dto.ErrorResponse;
import dashboard.com.smart_iot_dashboard.dto.GenerateClaimCodeResponse;
import dashboard.com.smart_iot_dashboard.exception.ClaimCodeNotFoundException;
import dashboard.com.smart_iot_dashboard.service.ProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Provisioning API", description = "Endpoints for claiming new devices")
public class ProvisioningController {

    private final ProvisioningService provisioningService;

    @PostMapping("/generate-claim-code")
    @Operation(summary = "Generate a temporary code to claim a device")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Code generated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GenerateClaimCodeResponse> generateClaimCode(@AuthenticationPrincipal Jwt jwt) {
        String claimCode = provisioningService.generateClaimCode(jwt.getSubject());
        return ResponseEntity.ok(new GenerateClaimCodeResponse(claimCode));
    }


    @PostMapping("/claim-with-code")
    @Operation(summary = "Claim a device using a previously generated code")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device claimed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid code format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Claim code not found or expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error during provisioning",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClaimResponse> claimWithCode(@Valid @RequestBody ClaimRequest claimRequest) {
        log.info("Device claim attempt with code: {}", claimRequest.getClaimCode());

        ClaimResponse response = provisioningService.claimDevice(claimRequest.getClaimCode());

        return ResponseEntity.ok(response);
    }
}
