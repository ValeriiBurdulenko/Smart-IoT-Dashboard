package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.User;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import dashboard.com.smart_iot_dashboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @Transactional(readOnly = true)
    public List<Device> findAllDevicesForCurrentUser() {
        return deviceRepository.findByUser_Username(getCurrentUsername());
    }

    @Transactional(readOnly = true)
    public Optional<Device> findDeviceByExternalIdForCurrentUser(String externalId) {
        return deviceRepository.findByExternalIdAndUser_Username(externalId, getCurrentUsername());
    }

    @Transactional
    public Device createDevice(Device device) {
        User currentUser = userRepository.findByUsername(getCurrentUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        device.setUser(currentUser);

        if (device.getExternalId() == null || device.getExternalId().isBlank()) {
            throw new IllegalArgumentException("External ID must not be empty");
        }
        if (deviceRepository.existsByExternalId(device.getExternalId())) {
            throw new IllegalArgumentException("Device with external ID " + device.getExternalId() + " already exists.");
        }

        return deviceRepository.save(device);
    }

    @Transactional
    public Optional<Device> updateDeviceByExternalId(String externalId, Device deviceDetails) {
        return deviceRepository.findByExternalIdAndUser_Username(externalId, getCurrentUsername())
                .map(existingDevice -> {
                    // Обновляем только те поля, которые можно менять
                    existingDevice.setName(deviceDetails.getName());
                    existingDevice.setLocation(deviceDetails.getLocation());
                    existingDevice.setStatus(deviceDetails.getStatus());
                    return deviceRepository.save(existingDevice);
                });
    }

    @Transactional
    public boolean deleteDeviceByExternalId(String externalId) {
        return deviceRepository.findByExternalIdAndUser_Username(externalId, getCurrentUsername())
                .map(device -> {
                    deviceRepository.deleteById(device.getId());
                    return true;
                }).orElse(false);
    }
}
