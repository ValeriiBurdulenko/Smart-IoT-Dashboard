package dashboard.com.smart_iot_dashboard.repository;

import dashboard.com.smart_iot_dashboard.entity.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("dev") // Aktiviert das 'dev'-Profil (nützlich, falls H2 dort konfiguriert ist)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DeviceRepositoryTest {

    @Autowired
    private TestEntityManager entityManager; // Helfer zum Vorbereiten von Testdaten

    @Autowired
    private DeviceRepository deviceRepository; // Das Repository, das wir testen

    // Wir verwenden String-IDs, da Keycloak keine 'User'-Entität mehr bereitstellt
    private final String USER_ID_1 = "keycloak-user-uuid-123";
    private final String USER_ID_2 = "keycloak-user-uuid-456";

    private Device activeDeviceUser1;
    private Device inactiveDeviceUser1;

    @BeforeEach
    void setUp() {
        // Erstelle Test-Geräte
        activeDeviceUser1 = new Device();
        activeDeviceUser1.setDeviceId("active-device-A");
        activeDeviceUser1.setHashedDeviceToken("hash-A");
        activeDeviceUser1.setUserId(USER_ID_1);
        activeDeviceUser1.setName("Active Device");
        activeDeviceUser1.setActive(true);
        entityManager.persist(activeDeviceUser1);

        inactiveDeviceUser1 = new Device();
        inactiveDeviceUser1.setDeviceId("inactive-device-B");
        inactiveDeviceUser1.setHashedDeviceToken("hash-B");
        inactiveDeviceUser1.setUserId(USER_ID_1);
        inactiveDeviceUser1.setName("Inactive Device");
        inactiveDeviceUser1.setActive(false);
        inactiveDeviceUser1.setDeactivatedAt(Instant.now().minus(40, ChronoUnit.DAYS));
        entityManager.persist(inactiveDeviceUser1);

        Device activeDeviceUser2 = new Device();
        activeDeviceUser2.setDeviceId("active-device-C");
        activeDeviceUser2.setHashedDeviceToken("hash-C");
        activeDeviceUser2.setUserId(USER_ID_2);
        activeDeviceUser2.setName("Other Active Device");
        activeDeviceUser2.setActive(true);
        entityManager.persist(activeDeviceUser2);

        entityManager.flush();
    }

    @Test
    void testFindByDeviceIdAndIsActiveTrue_FindsActive() {
        Optional<Device> found = deviceRepository.findByDeviceIdAndIsActiveTrue("active-device-A");
        assertThat(found).isPresent();
        assertThat(found.get().getDeviceId()).isEqualTo(activeDeviceUser1.getDeviceId());
    }

    @Test
    void testFindByDeviceIdAndIsActiveTrue_IgnoresInactive() {
        Optional<Device> found = deviceRepository.findByDeviceIdAndIsActiveTrue("inactive-device-B");
        assertThat(found).isNotPresent();
    }

    @Test
    void testFindByUserIdAndIsActiveTrue_FindsOnlyActive() {
        List<Device> user1Devices = deviceRepository.findByUserIdAndIsActiveTrue(USER_ID_1);
        assertThat(user1Devices).hasSize(1);
        assertThat(user1Devices.get(0).getDeviceId()).isEqualTo(activeDeviceUser1.getDeviceId());
    }

    @Test
    void testFindByDeviceIdAndUserIdAndIsActiveTrue_Success() {
        Optional<Device> found = deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue("active-device-A", USER_ID_1);
        assertThat(found).isPresent();
    }

    @Test
    void testFindByDeviceIdAndUserIdAndIsActiveTrue_FailsOnInactive() {
        Optional<Device> found = deviceRepository.findByDeviceIdAndUserIdAndIsActiveTrue("inactive-device-B", USER_ID_1);
        assertThat(found).isNotPresent();
    }

    @Test
    void testDeactivateDevicesByUserId() {
        // We perform a “soft removal” for ALL devices belonging to User 2
        int count = deviceRepository.deactivateDevicesByUserId(USER_ID_2, Instant.now());

        // Assert
        assertThat(count).isEqualTo(1);

        // Clear the Hibernate cache to force a read from the database
        entityManager.flush();
        entityManager.clear();

        // Verify that User 2 is now inactive
        Optional<Device> deactivatedDevice = deviceRepository.findByDeviceId("active-device-C");
        assertThat(deactivatedDevice).isPresent();
        assertThat(deactivatedDevice.get().isActive()).isFalse();
        assertThat(deactivatedDevice.get().getDeactivatedAt()).isNotNull();
    }

    @Test
    void testFindByIsActiveFalseAndDeactivatedAtBefore() {
        // Arrange
        // Searching for devices deleted more than 30 days ago
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        // Act
        List<Device> expiredDevices = deviceRepository.findByIsActiveFalseAndDeactivatedAtBefore(thirtyDaysAgo);

        // Assert
        // Must find our device, deleted 40 days ago
        assertThat(expiredDevices).hasSize(1);
        assertThat(expiredDevices.get(0).getDeviceId()).isEqualTo(inactiveDeviceUser1.getDeviceId());
    }
}