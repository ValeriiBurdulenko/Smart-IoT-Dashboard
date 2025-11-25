package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqttAclService {

    @Value("${mqtt.bridge.username}")
    private String bridgeUsername;

    private final DeviceRepository deviceRepository;

    private static final int MOSQ_ACL_READ = 1;
    private static final int MOSQ_ACL_WRITE = 2;
    private static final int MOSQ_ACL_SUBSCRIBE = 4;

    private static final String TELEMETRY_TOPIC = "iot/telemetry/ingress";
    private static final String DEVICES_PREFIX = "devices";
    private static final String COMMANDS_SUFFIX = "commands";

    @Transactional(readOnly = true)
    public boolean checkAcl(String deviceId, Integer accessType, String topic) {
        if (checkSystemBridge(deviceId, accessType, topic)) {
            return true;
        }
        return checkForRegularDevice(deviceId, accessType, topic);
    }

    private boolean checkSystemBridge(String deviceId, Integer accessType, String topic) {

        if (bridgeUsername == null || !bridgeUsername.equals(deviceId)) {
            return false;
        }

        if ((accessType == MOSQ_ACL_READ || accessType == MOSQ_ACL_SUBSCRIBE) &&
                TELEMETRY_TOPIC.equals(topic)) {
            return true;
        }

        if (accessType == MOSQ_ACL_WRITE && isValidDeviceCommandsTopic(topic)) {
            return true;
        }


        return false;
    }

    private boolean checkForRegularDevice(String deviceId, Integer accessType, String topic){
        boolean isActive = deviceRepository.findByDeviceIdAndIsActiveTrue(deviceId).isPresent();

        if (!isActive) {
            log.warn("ACL Check: Device '{}' is not active or does not exist. Denying.", deviceId);
            return false;
        }

        String expectedCommandTopic = String.format("%s/%s/%s", DEVICES_PREFIX, deviceId, COMMANDS_SUFFIX);

        if ((accessType == MOSQ_ACL_WRITE) && TELEMETRY_TOPIC.equals(topic)) {
            return true;
        } else if (((accessType == MOSQ_ACL_READ) || (accessType == MOSQ_ACL_SUBSCRIBE)) && topic.equals(expectedCommandTopic)) {
            return true;
        }

        log.debug("ACL Denied: Device '{}' tried accessing '{}' with acc {}", deviceId, topic, accessType);
        return false;
    }


    private boolean isValidDeviceCommandsTopic(String topic) {
        if (topic == null) return false;

        String[] parts = topic.split("/");

        return parts.length == 3 &&
                DEVICES_PREFIX.equals(parts[0]) &&
                !parts[1].isEmpty() &&
                COMMANDS_SUFFIX.equals(parts[2]);
    }
}
