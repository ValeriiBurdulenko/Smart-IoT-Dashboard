package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.dto.MqttAclRequest;
import dashboard.com.smart_iot_dashboard.service.MqttAclService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final int MOSQ_ACL_READ = 1;
    private static final int MOSQ_ACL_WRITE = 2;
    private static final int MOSQ_ACL_SUBSCRIBE = 4;

    private final MqttAclService aclService;


    @PostMapping("/acl")
    public ResponseEntity<Void> checkMqttAcl(@Valid @RequestBody MqttAclRequest aclRequest) {
        String deviceId = aclRequest.getUsername();
        String topic = aclRequest.getTopic();
        int accessType = aclRequest.getAcc();

        log.debug("MQTT ACL check request: DeviceId='{}', Topic='{}', AccessType='{}'",
                deviceId, topic, mapAccessTypeToString(accessType));

        if (aclService.checkAcl(deviceId, accessType, topic)) {
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
