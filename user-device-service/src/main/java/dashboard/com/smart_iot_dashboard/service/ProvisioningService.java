package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.dto.ClaimResponse;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.exception.ClaimCodeNotFoundException;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class ProvisioningService {

    private final StringRedisTemplate redisTemplate;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoderInternal;

    // You can configure the TTL in application.properties, e.g., claimcode.ttl-minutes=5
    @Value("${claimcode.ttl-minutes:5}")
    private long claimCodeTtlMinutes;

    private static final String CLAIM_CODE_PREFIX = "claimcode:";
    private static final SecureRandom secureRandom = new SecureRandom();

    public String generateClaimCode(String userId) {
        String claimCode;
        String redisKey;
        do {
            int codePart1 = ThreadLocalRandom.current().nextInt(100, 1000); // 100-999
            int codePart2 = ThreadLocalRandom.current().nextInt(100, 1000); // 100-999
            claimCode = String.format("%03d-%03d", codePart1, codePart2);
            redisKey = CLAIM_CODE_PREFIX + claimCode;
        } while (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)));

        redisTemplate.opsForValue().set(redisKey, userId, Duration.ofMinutes(claimCodeTtlMinutes));

        return claimCode;
    }


    @Transactional
    public ClaimResponse claimDevice(String claimCode) {
        String redisKey = CLAIM_CODE_PREFIX + claimCode;
        String userId = redisTemplate.opsForValue().get(redisKey);


        if (userId == null) {
            throw new ClaimCodeNotFoundException(claimCode);
        }

        String deviceId = UUID.randomUUID().toString();
        String deviceToken = generateSecureToken(32);
        String hashedToken = passwordEncoderInternal.encode(deviceToken);

        Device device = new Device();
        device.setDeviceId(deviceId);
        device.setHashedDeviceToken(hashedToken);
        device.setUserId(userId);
        device.setName("New Device " + deviceId.substring(0, 4));
        //device.setStatus("PROVISIONED"); // Example status
        deviceRepository.save(device);

        redisTemplate.delete(redisKey);

        return new ClaimResponse(deviceId, deviceToken);
    }

    private String generateSecureToken(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
