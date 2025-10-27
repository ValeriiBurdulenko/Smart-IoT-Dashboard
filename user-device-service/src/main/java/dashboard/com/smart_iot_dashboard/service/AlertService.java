package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Alert;
import dashboard.com.smart_iot_dashboard.repository.AlertRepository;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final DeviceRepository deviceRepository;

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @Transactional
    public Alert createAlert(Long deviceId, Alert alert) {
        return deviceRepository.findByIdAndUser_Username(deviceId, getCurrentUsername()).map(device -> {
            alert.setDevice(device);
            if (alert.getTimestamp() == null) {
                alert.setTimestamp(ZonedDateTime.now());
            }
            return alertRepository.save(alert);
        }).orElseThrow(() -> new RuntimeException("Device not found with id: " + deviceId));
    }

    @Transactional(readOnly = true)
    public List<Alert> findAlertsByDeviceId(Long deviceId) {
        String username = getCurrentUsername();
        if (!deviceRepository.existsByIdAndUser_Username(deviceId, username)) {
            throw new RuntimeException("Device with id " + deviceId + " not found or access denied.");
        }

        return alertRepository.findByDeviceIdAndDevice_User_Username(deviceId, username);
    }

    @Transactional
    public Optional<Alert> acknowledgeAlert(Long alertId) {


        return alertRepository.findByIdAndDevice_User_Username(alertId, getCurrentUsername())
                .map(alert -> {
                    alert.setAcknowledged(true);
                    return alertRepository.save(alert);
                });
    }
}
