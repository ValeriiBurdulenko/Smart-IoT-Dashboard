package dashboard.com.smart_iot_dashboard.repository;

import dashboard.com.smart_iot_dashboard.entity.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("dev") // Aktiviert das 'dev'-Profil (nützlich, falls H2 dort konfiguriert ist)
class DeviceRepositoryTest {

    @Autowired
    private TestEntityManager entityManager; // Helfer zum Vorbereiten von Testdaten

    @Autowired
    private DeviceRepository deviceRepository; // Das Repository, das wir testen

    // Wir verwenden String-IDs, da Keycloak keine 'User'-Entität mehr bereitstellt
    private final String USER_ID_1 = "keycloak-user-uuid-123";
    private final String USER_ID_2 = "keycloak-user-uuid-456";

    private Device deviceA;
    private Device deviceB;

    @BeforeEach
    void setUp() {
        // Erstelle Test-Geräte
        deviceA = new Device();
        deviceA.setDeviceId("device-A");
        deviceA.setHashedDeviceToken("hash-A");
        deviceA.setUserId(USER_ID_1);
        deviceA.setName("Device A");
        entityManager.persist(deviceA);

        deviceB = new Device();
        deviceB.setDeviceId("device-B");
        deviceB.setHashedDeviceToken("hash-B");
        deviceB.setUserId(USER_ID_1);
        deviceB.setName("Device B");
        entityManager.persist(deviceB);

        Device deviceC = new Device();
        deviceC.setDeviceId("device-C");
        deviceC.setHashedDeviceToken("hash-C");
        deviceC.setUserId(USER_ID_2);
        deviceC.setName("Device C");
        entityManager.persist(deviceC);

        entityManager.flush();
    }

    @Test
    void testFindByDeviceId_Success() {
        Optional<Device> found = deviceRepository.findByDeviceId("device-A");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Device A");
    }

    @Test
    void testExistsByDeviceId_Success() {
        boolean exists = deviceRepository.existsByDeviceId("device-A");
        assertThat(exists).isTrue();
    }

    @Test
    void testExistsByDeviceId_Failure() {
        boolean exists = deviceRepository.existsByDeviceId("non-existing-device");
        assertThat(exists).isFalse();
    }

    @Test
    void testFindByUserId() {
        List<Device> user1Devices = deviceRepository.findByUserId(USER_ID_1);
        assertThat(user1Devices).hasSize(2).contains(deviceA, deviceB);
    }

    @Test
    void testFindByDeviceIdAndUserId_Success() {
        Optional<Device> found = deviceRepository.findByDeviceIdAndUserId("device-A", USER_ID_1);
        assertThat(found).isPresent();
    }

    @Test
    void testFindByDeviceIdAndUserId_WrongOwner() {
        Optional<Device> found = deviceRepository.findByDeviceIdAndUserId("device-A", USER_ID_2);
        assertThat(found).isNotPresent();
    }
}