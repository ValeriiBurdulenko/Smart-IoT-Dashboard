package dashboard.com.smart_iot_dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dashboard.com.smart_iot_dashboard.dto.AlertDTO;
import dashboard.com.smart_iot_dashboard.dto.IncomingAlertDTO;
import dashboard.com.smart_iot_dashboard.entity.Alert;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.exception.*;
import dashboard.com.smart_iot_dashboard.repository.AlertRepository;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Alert processIncomingAlert(String payload) {
        try {
            IncomingAlertDTO dto = objectMapper.readValue(payload, IncomingAlertDTO.class);

            // 1. Checking duplicates
            if (dto.getAlertId() != null && alertRepository.existsByAlertId(dto.getAlertId())) {
                log.warn("Duplicate alert detected: {}", dto.getAlertId());
                throw new DuplicateAlertException(dto.getAlertId());
            }

            // 2. Find userId of Device
            Device device = deviceRepository.findByDeviceIdAndIsActiveTrue(dto.getDeviceId())
                    .orElseThrow(() -> new DeviceNotFoundException(dto.getDeviceId()));

            // 3. Save Alert
            Alert alert = Alert.builder()
                    .alertId(dto.getAlertId())
                    .deviceId(dto.getDeviceId())
                    .userId(device.getUserId())
                    .type(dto.getType())
                    .message(dto.getMessage())
                    .value(dto.getValue() != null ? dto.getValue() : 0.0)
                    .timestamp(dto.getTimestamp() != null ? dto.getTimestamp() : Instant.now())
                    .isRead(false)
                    .build();

            Alert savedAlert = alertRepository.save(alert);

            log.info("🚨 Alert saved for user {}: {}", device.getUserId(), dto.getType());

            return savedAlert;

        } catch (Exception e) {
            log.error("Failed to process alert payload: {}", payload, e);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Page<AlertDTO> getUserAlerts(String userId, boolean unreadOnly, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new ValidationException("Invalid pagination parameters", "INVALID_PAGINATION");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Alert> alertsPage;

        if (unreadOnly) {
            alertsPage = alertRepository.findByUserIdAndIsReadFalseOrderByTimestampDesc(userId, pageable);
        } else {
            alertsPage = alertRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
        }

        return alertsPage.map(this::convertToDTO);
    }

    @Transactional
    public void markAsRead(String alertId, String userId) {
        Alert alert = alertRepository.findByAlertIdAndUserId(alertId, userId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));
        alert.setRead(true);
        alertRepository.save(alert);
    }

    @Transactional
    public void markAllAsRead(String userId) {
        if (userId == null) throw new AuthenticationException("User ID missing", "AUTH_REQUIRED");
        alertRepository.markAllAsRead(userId);
    }

    @Transactional
    public void deleteAlert(String alertId, String userId) {
        Alert alert = alertRepository.findByAlertIdAndUserId(alertId, userId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));
        alertRepository.delete(alert);
    }

    private AlertDTO convertToDTO(Alert alert) {
        return AlertDTO.builder()
                .alertId(alert.getAlertId())
                .deviceId(alert.getDeviceId())
                .type(alert.getType())
                .message(alert.getMessage())
                .value(alert.getValue())
                .timestamp(alert.getTimestamp())
                .isRead(alert.isRead())
                .build();
    }
}
