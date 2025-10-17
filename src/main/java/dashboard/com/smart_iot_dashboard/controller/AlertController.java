package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.entity.Alert;
import dashboard.com.smart_iot_dashboard.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/devices/{deviceId}/alerts")
    public List<Alert> getAlertsForDevice(@PathVariable Long deviceId) {
        try {
            return alertService.findAlertsByDeviceId(deviceId);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }


    @PostMapping("/devices/{deviceId}/alerts")
    @ResponseStatus(HttpStatus.CREATED)
    public Alert createAlert(@PathVariable Long deviceId, @RequestBody Alert alert) {
        try {
            return alertService.createAlert(deviceId, alert);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PutMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<Alert> acknowledgeAlert(@PathVariable Long alertId) {
        return alertService.acknowledgeAlert(alertId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}