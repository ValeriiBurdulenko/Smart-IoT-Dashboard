package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.entity.Alert;
import dashboard.com.smart_iot_dashboard.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    // Получить все алерты для конкретного устройства
    @GetMapping("/devices/{deviceId}/alerts")
    public List<Alert> getAlertsForDevice(@PathVariable Long deviceId) {
        return alertService.findAlertsByDeviceId(deviceId);
    }

    // Подтвердить (квитировать) алерт
    @PutMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<Alert> acknowledgeAlert(@PathVariable Long alertId) {
        return alertService.acknowledgeAlert(alertId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
