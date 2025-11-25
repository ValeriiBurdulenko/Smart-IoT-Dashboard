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

    @Transactional(readOnly = true)
    public boolean checkAcl(String deviceId, Integer accessType, String topic) {
        if (checkSystemBridge(deviceId, accessType, topic)) {
            return true;
        }
        return checkForRegularDevice(deviceId, accessType, topic);
    }

    private boolean checkSystemBridge(String deviceId, Integer accessType, String topic) {

        if (bridgeUsername == null || deviceId == null || !bridgeUsername.equals(deviceId) || accessType == null) {
            return false;
        }

        if ((accessType == MOSQ_ACL_READ || accessType == MOSQ_ACL_SUBSCRIBE) &&
                topic.equals("iot/telemetry/ingress")) {
            return true;
        }

        if (accessType == MOSQ_ACL_WRITE && isValidDeviceCommandsTopic(topic)) {
            return true;
        }


        return false;
    }

    private boolean checkForRegularDevice(String deviceId, Integer accessType, String topic){
        if (deviceId == null || topic == null || accessType == null) {
            return false;
        }

        boolean isActive = deviceRepository.findByDeviceIdAndIsActiveTrue(deviceId).isPresent();

        if (!isActive) {
            log.warn("ACL Check: Device '{}' is not active or does not exist. Denying.", deviceId);
            return false;
        }

        String expectedTelemetryTopic = "iot/telemetry/ingress";
        String expectedCommandTopic = "devices/" + deviceId + "/commands";

        if ((accessType == MOSQ_ACL_WRITE) && topic.equals(expectedTelemetryTopic)) {
            return true;
        } else if (((accessType == MOSQ_ACL_READ) || (accessType == MOSQ_ACL_SUBSCRIBE)) && topic.equals(expectedCommandTopic)) {
            return true;
        }

        return false;
    }


    private boolean isValidDeviceCommandsTopic(String topic) {
        if (topic == null) return false;

        String[] parts = topic.split("/");

        return parts.length == 3 &&
                "devices".equals(parts[0]) &&
                !parts[1].isEmpty() &&
                "commands".equals(parts[2]);
    }
}
