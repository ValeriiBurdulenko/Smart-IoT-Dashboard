package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Alert;
import dashboard.com.smart_iot_dashboard.repository.AlertRepository;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public Alert createAlert(Long deviceId, Alert alert) {
        return deviceRepository.findById(deviceId).map(device -> {
            alert.setDevice(device);
            return alertRepository.save(alert);
        }).orElseThrow(() -> new RuntimeException("Device not found with id: " + deviceId));
    }

    @Transactional(readOnly = true)
    public List<Alert> findAlertsByDeviceId(Long deviceId) {
        return alertRepository.findByDeviceId(deviceId);
    }

    @Transactional
    public Optional<Alert> acknowledgeAlert(Long alertId) {
        return alertRepository.findById(alertId)
                .map(alert -> {
                    alert.setAcknowledged(true);
                    return alertRepository.save(alert);
                });
    }
}
