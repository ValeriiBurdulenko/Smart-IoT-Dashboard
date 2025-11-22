package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.MqttAclRequest;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/mqtt")
@RequiredArgsConstructor
@Slf4j
public class MqttAclController {

    @Value("${mqtt.bridge.username}")
    private String bridgeUsername;
    private final DeviceRepository deviceRepository;

    private static final int MOSQ_ACL_READ = 1;
    private static final int MOSQ_ACL_WRITE = 2;
    private static final int MOSQ_ACL_SUBSCRIBE = 4;


    @PostMapping("/acl")
    public ResponseEntity<Void> checkMqttAcl(@RequestBody MqttAclRequest aclRequest) {
        String deviceId = aclRequest.getUsername();
        String topic = aclRequest.getTopic();
        int accessType = aclRequest.getAcc();
        boolean allowed = false;

        log.debug("MQTT ACL check request: DeviceId='{}', Topic='{}', AccessType='{}'",
                deviceId, topic, mapAccessTypeToString(accessType));


        if(checkSystemBridge(deviceId, accessType, topic)){
            allowed = true;
        } else if(checkForRegularDevice(deviceId, accessType, topic)) allowed = true;


        if (allowed) {
            log.info("MQTT ACL allowed: DeviceId='{}', Topic='{}', AccessType='{}'",
                    deviceId, topic, mapAccessTypeToString(accessType));
            return ResponseEntity.ok().build();
        } else {
            log.warn("MQTT ACL denied: DeviceId='{}', Topic='{}', AccessType='{}'",
                    deviceId, topic, mapAccessTypeToString(accessType));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    private boolean checkSystemBridge(String deviceId, Integer accessType,String topic) {
        if (bridgeUsername != null && bridgeUsername.equals(deviceId)) {

            if ((accessType == MOSQ_ACL_READ || accessType == MOSQ_ACL_SUBSCRIBE) &&
                    topic.equals("iot/telemetry/ingress")) {
                return true;
            }

            if (accessType == MOSQ_ACL_WRITE &&
                    topic.startsWith("devices/") &&
                    topic.endsWith("/commands")) {
                return true;
            }
        }


        return false;
    }

    private boolean checkForRegularDevice(String deviceId, Integer accessType,String topic){
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

    private String mapAccessTypeToString(int acc) {
        switch (acc) {
            case MOSQ_ACL_READ: return "READ(" + acc + ")";
            case MOSQ_ACL_WRITE: return "WRITE(" + acc + ")";
            case MOSQ_ACL_SUBSCRIBE: return "SUBSCRIBE(" + acc + ")";
            default: return "UNKNOWN(" + acc + ")";
        }
    }
}
