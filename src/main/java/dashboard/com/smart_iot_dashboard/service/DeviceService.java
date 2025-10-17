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
    public Optional<Device> findDeviceByIdForCurrentUser(Long id) {
        return deviceRepository.findByIdAndUser_Username(id, getCurrentUsername());
    }

    @Transactional
    public Device createDevice(Device device) {
        User currentUser = userRepository.findByUsername(getCurrentUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        device.setUser(currentUser);
        return deviceRepository.save(device);
    }

    @Transactional
    public Optional<Device> updateDevice(Long id, Device deviceDetails) {
        return deviceRepository.findByIdAndUser_Username(id, getCurrentUsername())
                .map(existingDevice -> {
                    existingDevice.setName(deviceDetails.getName());
                    existingDevice.setLocation(deviceDetails.getLocation());
                    existingDevice.setStatus(deviceDetails.getStatus());
                    return deviceRepository.save(existingDevice);
                });
    }

    @Transactional
    public boolean deleteDevice(Long id) {
        return deviceRepository.findByIdAndUser_Username(id, getCurrentUsername())
                .map(device -> {
                    deviceRepository.deleteById(id);
                    return true;
                }).orElse(false);
    }
}
