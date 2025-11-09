package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DataRetentionService dataRetentionService;

    private Device expiredDevice1;
    private final String expiredDevice1_Id = "expired-device-1";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dataRetentionService, "deleteTopic", "iot-device-deletions");
        ReflectionTestUtils.setField(dataRetentionService, "retentionDays", 30L);

        expiredDevice1 = new Device();
        expiredDevice1.setDeviceId(expiredDevice1_Id);
    }

    @Test
    void purgeExpiredDevices_shouldSendEventsAndHardDelete() throws Exception {
        // Arrange
        when(deviceRepository.findByIsActiveFalseAndDeactivatedAtBefore(any(Instant.class)))
                .thenReturn(List.of(expiredDevice1));

        String expectedKafkaPayload = "{\"deviceId\":\"" + expiredDevice1_Id + "\",\"action\":\"PURGE\"}";
        when(objectMapper.writeValueAsString(any(Object.class))).thenReturn(expectedKafkaPayload);

        // Act
        dataRetentionService.purgeExpiredDevices();

        // Assert
        verify(kafkaTemplate).send(
                eq("iot-device-deletions"),
                eq(expiredDevice1_Id),
                eq(expectedKafkaPayload)
        );

        verify(deviceRepository).delete(eq(expiredDevice1));
    }

    @Test
    void purgeExpiredDevices_shouldDoNothing_whenNoDevicesFound() {
        // Arrange
        when(deviceRepository.findByIsActiveFalseAndDeactivatedAtBefore(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Act
        dataRetentionService.purgeExpiredDevices();

        // Assert
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(deviceRepository, never()).delete(any(Device.class));
    }
}
