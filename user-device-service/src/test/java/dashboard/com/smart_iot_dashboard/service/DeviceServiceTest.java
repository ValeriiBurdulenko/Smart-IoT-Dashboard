package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.entity.User;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import dashboard.com.smart_iot_dashboard.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void createDevice_shouldSetCurrentUserAsOwner() {
        // Arrange
        String username = "owner";
        User user = new User();
        user.setId(1L);
        user.setUsername(username);

        Device device = new Device();
        device.setName("Test Device");
        device.setExternalId("etwas");

        when(authentication.getName()).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Device createdDevice = deviceService.createDevice(device);

        // Assert
        assertThat(createdDevice).isNotNull();
        assertThat(createdDevice.getUser()).isNotNull();
        assertThat(createdDevice.getUser().getUsername()).isEqualTo(username);

        verify(deviceRepository, times(1)).save(device);
    }

    @Test
    void deleteDevice_shouldDelete_whenDeviceBelongsToUser() {
        // Arrange
        String username = "owner";
        String deviceId = "1L";
        Device device = new Device();
        device.setExternalId(deviceId);

        when(authentication.getName()).thenReturn(username);
        when(deviceRepository.findByExternalIdAndUser_Username(deviceId, username)).thenReturn(Optional.of(device));

        // Act
        boolean result = deviceService.deleteDeviceByExternalId(deviceId);

        // Assert
        assertThat(result).isTrue();
        verify(deviceRepository, times(1)).deleteById(device.getId());
    }

    @Test
    void deleteDevice_shouldNotDelete_whenDeviceNotBelongsToUser() {
        // Arrange
        String username = "owner";
        String deviceId = "1L";

        when(authentication.getName()).thenReturn(username);
        when(deviceRepository.findByExternalIdAndUser_Username(deviceId, username)).thenReturn(Optional.empty());

        // Act
        boolean result = deviceService.deleteDeviceByExternalId(deviceId);

        // Assert
        assertThat(result).isFalse();
        verify(deviceRepository, never()).deleteById(anyLong());
    }
}
