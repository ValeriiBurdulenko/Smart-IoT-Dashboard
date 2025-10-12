package dashboard.com.smart_iot_dashboard.controller;

import dashboard.com.smart_iot_dashboard.entity.User;
import dashboard.com.smart_iot_dashboard.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@RequestBody User user) {
        // !! ВАЖНО: Пароль должен быть захеширован в сервисе перед сохранением
        // user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userService.createUser(user);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAllUsers();
    }
}
