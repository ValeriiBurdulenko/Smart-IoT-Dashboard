package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void deleteDeviceByUser_shouldSoftDelete_whenDeviceExistsAndOwned() {
        // Arrange
        String deviceId = "device-to-delete";
        String userId = "owner-user-id";

        Device mockDevice = new Device();
        mockDevice.setDeviceId(deviceId);
        mockDevice.setUserId(userId);
        mockDevice.setActive(true);


        when(deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue(deviceId, userId))
                .thenReturn(Optional.of(mockDevice));

        // Act
        boolean result = deviceService.deleteDeviceByUser(deviceId, userId);

        // Assert
        assertThat(result).isTrue();

        // 1. Check that save() was called, NOT delete()
        verify(deviceRepository, never()).delete(any(Device.class));

        // 2. Catch the object that was passed to save()
        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(deviceCaptor.capture());

        Device savedDevice = deviceCaptor.getValue();

        // 3. Check that the saved object has the flag isActive = false and the date is set
        assertThat(savedDevice.isActive()).isFalse();
        assertThat(savedDevice.getDeactivatedAt()).isNotNull();
    }

    @Test
    void deleteDeviceByUser_shouldReturnFalse_whenDeviceNotFound() {
        // Arrange
        String deviceId = "non-existing-device";
        String userId = "owner-user-id";

        // Configuring the repository mock: device not found
        when(deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue(deviceId, userId))
                .thenReturn(Optional.empty());

        // Act
        boolean result = deviceService.deleteDeviceByUser(deviceId, userId);

        // Assert
        assertThat(result).isFalse(); // Method should return false
        verify(deviceRepository, never()).save(any(Device.class));
    }
}
