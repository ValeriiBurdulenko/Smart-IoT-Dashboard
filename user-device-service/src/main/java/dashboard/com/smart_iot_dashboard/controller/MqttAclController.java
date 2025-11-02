package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.MqttAclRequest;
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

    private static final int MOSQ_ACL_READ = 1;
    private static final int MOSQ_ACL_WRITE = 2;
    private static final int MOSQ_ACL_SUBSCRIBE = 4;


    @PostMapping("/acl")
    public ResponseEntity<Void> checkMqttAcl(@RequestBody MqttAclRequest aclRequest) {
        String deviceId = aclRequest.getUsername();
        String topic = aclRequest.getTopic();
        int accessType = aclRequest.getAcc();

        log.debug("MQTT ACL check request: DeviceId='{}', Topic='{}', AccessType='{}'",
                deviceId, topic, mapAccessTypeToString(accessType));

        boolean allowed = false;


        // 1. CHECK FOR SYSTEM BRIDGE
        if (bridgeUsername != null && bridgeUsername.equals(deviceId)) {
            if ((accessType == MOSQ_ACL_READ || accessType == MOSQ_ACL_SUBSCRIBE) &&
                    topic.equals("iot/telemetry/ingress")) {
                allowed = true;
            }
        }
        // 2. CHECK FOR REGULAR DEVICES
        else {
            String expectedTelemetryTopic = "iot/telemetry/ingress";
            String expectedCommandTopic = "devices/" + deviceId + "/commands";

            if (accessType == MOSQ_ACL_WRITE) {
                if (topic.equals(expectedTelemetryTopic)) {
                    allowed = true;
                }
            } else if (accessType == MOSQ_ACL_READ || accessType == MOSQ_ACL_SUBSCRIBE) {
                if (topic.equals(expectedCommandTopic)) {
                    allowed = true;
                }
            }
        }

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

    private String mapAccessTypeToString(int acc) {
        switch (acc) {
            case MOSQ_ACL_READ: return "READ(" + acc + ")";
            case MOSQ_ACL_WRITE: return "WRITE(" + acc + ")";
            case MOSQ_ACL_SUBSCRIBE: return "SUBSCRIBE(" + acc + ")";
            default: return "UNKNOWN(" + acc + ")";
        }
    }
}
