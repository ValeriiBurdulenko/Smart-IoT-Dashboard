package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/devices") // Версионирование API
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public List<Device> getAllDevices() {
        return deviceService.findAllDevices();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Device> getDeviceById(@PathVariable Long id) {
        return deviceService.findDeviceById(id)
                .map(ResponseEntity::ok) // Если найдено, вернуть 200 OK с телом
                .orElse(ResponseEntity.notFound().build()); // Иначе вернуть 404 Not Found
    }

    // Примечание: в реальном приложении userId должен браться из сессии/токена аутентифицированного пользователя
    // Здесь для простоты мы ожидаем его в теле запроса.
    // Пример POST-запроса на http://localhost:8080/api/v1/devices?userId=1
    @PostMapping
    public ResponseEntity<Device> createDevice(@RequestBody Device device, @RequestParam Long userId) {
        Device createdDevice = deviceService.createDevice(device, userId);

        // Формируем URI для нового ресурса для ответа 201 Created
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdDevice.getId())
                .toUri();

        return ResponseEntity.created(location).body(createdDevice);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Device> updateDevice(@PathVariable Long id, @RequestBody Device deviceDetails) {
        return deviceService.updateDevice(id, deviceDetails)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable Long id) {
        if (deviceService.deleteDevice(id)) {
            return ResponseEntity.noContent().build(); // 204 No Content
        } else {
            return ResponseEntity.notFound().build(); // 404 Not Found
        }
    }
}
