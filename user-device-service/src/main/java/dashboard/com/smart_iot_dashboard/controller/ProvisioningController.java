package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.ClaimRequest;
import dashboard.com.smart_iot_dashboard.dto.ClaimResponse;
import dashboard.com.smart_iot_dashboard.dto.GenerateClaimCodeResponse;
import dashboard.com.smart_iot_dashboard.exception.ClaimCodeNotFoundException;
import dashboard.com.smart_iot_dashboard.service.ProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class ProvisioningController {

    private final ProvisioningService provisioningService;

    @PostMapping("/generate-claim-code")
    public ResponseEntity<GenerateClaimCodeResponse> generateClaimCode(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID not found in token");
        }

        String claimCode = provisioningService.generateClaimCode(userId);
        return ResponseEntity.ok(new GenerateClaimCodeResponse(claimCode));
    }


    @PostMapping("/claim-with-code")
    public ResponseEntity<ClaimResponse> claimWithCode(@Valid @RequestBody ClaimRequest claimRequest) {
        try {
            ClaimResponse response = provisioningService.claimDevice(claimRequest.getClaimCode());
            return ResponseEntity.ok(response);
        } catch (ClaimCodeNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error claiming device", e);
        }
    }
}
