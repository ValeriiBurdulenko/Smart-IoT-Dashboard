package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.service.DeviceService;
import dashboard.com.smart_iot_dashboard.service.MqttGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final MqttGateway mqttGateway;

    @GetMapping
    public List<Device> getAllDevicesForCurrentUser() {
        return deviceService.findAllDevicesForCurrentUser();
    }

    /**
     * ✅ Ruft Details zu einem bestimmten Gerät anhand seiner externalId ab.
     * Endpunkt: GET /api/v1/devices/{externalId}
     */
    @GetMapping("/{externalId}")
    public ResponseEntity<Device> getDeviceByExternalId(@PathVariable String externalId) {
        return deviceService.findDeviceByExternalIdForCurrentUser(externalId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * ✅ Erstellt ein neues Gerät. externalId muss im Hauptteil der Anfrage angegeben werden.
     * Endpunkt: POST /api/v1/devices
     */
    @PostMapping
    public ResponseEntity<Device> createDevice(@RequestBody Device device) {
        try {
            Device createdDevice = deviceService.createDevice(device);

            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{externalId}")
                    .buildAndExpand(createdDevice.getExternalId())
                    .toUri();

            return ResponseEntity.created(location).body(createdDevice);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * ✅ Aktualisiert die Informationen zum Gerät anhand seiner externalId.
     * Endpunkt: PUT /api/v1/devices/{externalId}
     */
    @PutMapping("/{externalId}")
    public ResponseEntity<Device> updateDevice(@PathVariable String externalId, @RequestBody Device deviceDetails) {
        return deviceService.updateDeviceByExternalId(externalId, deviceDetails)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * ✅ Löscht das Gerät anhand seiner externalId.
     * Endpunkt: DELETE /api/v1/devices/{externalId}
     */
    @DeleteMapping("/{externalId}")
    public ResponseEntity<Void> deleteDevice(@PathVariable String externalId) {
        if (deviceService.deleteDeviceByExternalId(externalId)) {
            return ResponseEntity.noContent().build(); // Статус 204
        } else {
            return ResponseEntity.notFound().build(); // Статус 404
        }
    }

    /**
     * ✅ Sendet einen Befehl an das Gerät anhand seiner externalId.
     * Endpunkt: POST /api/v1/devices/{externalId}/commands
     */
    @PostMapping("/{externalId}/commands")
    public ResponseEntity<Void> sendCommandToDevice(
            @PathVariable String externalId,
            @RequestBody String commandPayload) {

        return deviceService.findDeviceByExternalIdForCurrentUser(externalId)
                .map(device -> {
                    String commandTopic = "devices/" + device.getExternalId() + "/commands";
                    mqttGateway.sendCommand(commandPayload, commandTopic);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
