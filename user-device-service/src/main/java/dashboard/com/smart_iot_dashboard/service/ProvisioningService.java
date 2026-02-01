package dashboard.com.smart_iot_dashboard.service;

import dashboard.com.smart_iot_dashboard.dto.ClaimResponse;
import dashboard.com.smart_iot_dashboard.entity.Device;
import dashboard.com.smart_iot_dashboard.exception.*;
import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        if (userId == null || userId.isBlank()) {
            throw new AuthenticationException("User ID must be provided to generate claim code", "AUTH_MISSING_USER_ID");
        }

        String claimCode;
        String redisKey;
        do {
            int codePart1 = ThreadLocalRandom.current().nextInt(100, 1000); // 100-999
            int codePart2 = ThreadLocalRandom.current().nextInt(100, 1000); // 100-999
            claimCode = String.format("%03d-%03d", codePart1, codePart2);
            redisKey = CLAIM_CODE_PREFIX + claimCode;
        } while (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)));

        redisTemplate.opsForValue().set(redisKey, userId, Duration.ofMinutes(claimCodeTtlMinutes));
        log.info("Generated claim code for user: {}", userId);

        return claimCode;
    }


    @Transactional
    public ClaimResponse claimDevice(String claimCode) {

        if (claimCode == null || !claimCode.matches("\\d{3}-\\d{3}")) {
            throw new ValidationException("Invalid claim code format. Expected format: 000-000", "INVALID_CLAIM_CODE_FORMAT");
        }
        String redisKey = CLAIM_CODE_PREFIX + claimCode;
        String userId;

        try {
            userId = redisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            log.error("Redis connection error during claim: {}", e.getMessage());
            throw new ServiceUnavailableException("Session storage is temporarily unavailable", "REDIS_UNAVAILABLE");
        }


        if (userId == null) {
            throw new ClaimCodeNotFoundException(claimCode);
        }

        try {
            String deviceId = UUID.randomUUID().toString();
            String deviceToken = generateSecureToken();
            String hashedToken = passwordEncoderInternal.encode(deviceToken);

            Device device = new Device();
            device.setDeviceId(deviceId);
            device.setHashedDeviceToken(hashedToken);
            device.setUserId(userId);
            device.setName("New Device " + deviceId.substring(0, 4));
            device.setActive(true);
            deviceRepository.save(device);

            redisTemplate.delete(redisKey);

            log.info("Device {} successfully claimed by user {}", deviceId, userId);
            return new ClaimResponse(deviceId, deviceToken);
        } catch (Exception e) {
            log.error("Critical error during device provision: {}", e.getMessage());
            throw new InternalServerException("Could not complete device provisioning", e);
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
