package dashboard.com.smart_iot_dashboard.repositrory;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.entity.User;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DeviceRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DeviceRepository deviceRepository;

    private User user1;
    private User user2;
    private Device device1;

    @BeforeEach
    void setUp() {
        user1 = new User();
        user1.setUsername("user1");
        user1.setPassword("pass");
        user1.setRole("USER");
        entityManager.persist(user1);

        user2 = new User();
        user2.setUsername("user2");
        user2.setPassword("pass");
        user2.setRole("USER");
        entityManager.persist(user2);

        device1 = new Device();
        device1.setName("User1's Device");
        device1.setUser(user1);
        device1.setStatus("ACTIVE");
        entityManager.persist(device1);

        Device device2 = new Device();
        device2.setName("Another User1's Device");
        device2.setUser(user1);
        device2.setStatus("INACTIVE");
        entityManager.persist(device2);

        Device device3 = new Device();
        device3.setName("User2's Device");
        device3.setUser(user2);
        device3.setStatus("ACTIVE");
        entityManager.persist(device3);

        entityManager.flush();
    }

    @Test
    void findByUser_Username_shouldReturnOnlyUserDevices() {
        List<Device> foundDevices = deviceRepository.findByUser_Username("user1");

        assertThat(foundDevices).hasSize(2);
        assertThat(foundDevices).extracting(Device::getName)
                .containsExactlyInAnyOrder("User1's Device", "Another User1's Device");
    }

    @Test
    void findByIdAndUser_Username_shouldReturnDevice_whenOwnerIsCorrect() {
        Optional<Device> foundDevice = deviceRepository.findByIdAndUser_Username(device1.getId(), "user1");

        assertThat(foundDevice).isPresent();
        assertThat(foundDevice.get().getId()).isEqualTo(device1.getId());
    }

    @Test
    void findByIdAndUser_Username_shouldReturnEmpty_whenOwnerIsIncorrect() {
        Optional<Device> foundDevice = deviceRepository.findByIdAndUser_Username(device1.getId(), "user2");

        assertThat(foundDevice).isNotPresent();
    }
}