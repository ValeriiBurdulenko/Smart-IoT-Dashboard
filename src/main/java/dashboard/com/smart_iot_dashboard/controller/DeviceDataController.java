package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.entity.DeviceData;
import dashboard.com.smart_iot_dashboard.service.DeviceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/data")
@RequiredArgsConstructor
public class DeviceDataController {

    private final DeviceDataService deviceDataService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceData submitData(@PathVariable Long deviceId, @RequestBody DeviceData data) {
        try {
            return deviceDataService.saveData(deviceId, data);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @GetMapping
    public List<DeviceData> getDataForDevice(@PathVariable Long deviceId) {
        try {
            return deviceDataService.findDataByDeviceId(deviceId);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }
}