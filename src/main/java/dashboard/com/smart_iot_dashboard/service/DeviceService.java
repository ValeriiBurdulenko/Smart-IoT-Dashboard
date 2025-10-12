package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import dashboard.com.smart_iot_dashboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Device> findAllDevices() {
        return deviceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Device> findDeviceById(Long id) {
        return deviceRepository.findById(id);
    }

    @Transactional
    public Device createDevice(Device device, Long userId) {
        // Find the user who will own the device
        return userRepository.findById(userId).map(user -> {
            device.setUser(user);
            return deviceRepository.save(device);
        }).orElseThrow(() -> new RuntimeException("User not found with id: " + userId)); //TODO In the future, we will replace it with a custom exception.
    }

    @Transactional
    public Optional<Device> updateDevice(Long id, Device deviceDetails) {
        return deviceRepository.findById(id)
                .map(existingDevice -> {
                    existingDevice.setName(deviceDetails.getName());
                    existingDevice.setLocation(deviceDetails.getLocation());
                    existingDevice.setStatus(deviceDetails.getStatus());
                    return deviceRepository.save(existingDevice);
                });
    }

    @Transactional
    public boolean deleteDevice(Long id) {
        if (deviceRepository.existsById(id)) {
            deviceRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
