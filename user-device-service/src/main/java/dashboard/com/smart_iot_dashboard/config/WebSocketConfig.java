package dashboard.com.smart_iot_dashboard.config;

import dashboard.com.smart_iot_dashboard.repository.DeviceRepository;
import org.springframework.lang.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitHost;

    @Value("${spring.rabbitmq.stomp.port:61613}")
    private int rabbitStompPort;

    @Value("${spring.rabbitmq.username:guest}")
    private String rabbitUsername;

    @Value("${spring.rabbitmq.password:guest}")
    private String rabbitPassword;

    @Value("${websocket.allowed-origins}")
    private String allowedOrigins;

    private final JwtDecoder jwtDecoder;

    private final DeviceRepository deviceRepository;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost(rabbitHost)
                .setRelayPort(rabbitStompPort)
                .setClientLogin(rabbitUsername)
                .setClientPasscode(rabbitPassword)
                .setSystemLogin(rabbitUsername)
                .setSystemPasscode(rabbitPassword);

        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    return handleConnect(accessor, message);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    return handleSubscribe(accessor, message);
                }

                return message;
            }
        });
    }

    // --- Helper Methods to reduce Cognitive Complexity ---

    private Message<?> handleConnect(StompHeaderAccessor accessor, Message<?> message) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                Jwt jwt = jwtDecoder.decode(token);
                JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
                accessor.setUser(auth);
                log.info("WS Connected: User {}", auth.getName());
                return message;
            } catch (Exception e) {
                log.error("WS Auth Failed: {}", e.getMessage());
                return null;
            }
        }
        log.warn("WS Connection attempt without token");
        return null;
    }

    private Message<?> handleSubscribe(StompHeaderAccessor accessor, Message<?> message) {
        String destination = accessor.getDestination();
        Principal user = accessor.getUser();

        if (destination != null && destination.startsWith("/topic/device.")) {
            if (user == null) {
                log.warn("Unauthenticated user tried to subscribe to {}", destination);
                return null;
            }

            String requestedDeviceId = destination.substring("/topic/device.".length());
            String userId = user.getName();

            log.debug("User {} trying to subscribe to device {}", userId, requestedDeviceId);

            if (!deviceRepository.existsByDeviceIdAndUserIdAndIsActiveTrue(requestedDeviceId, userId)) {
                log.warn("WS Security Alert: User {} tried to spy on device {}", userId, requestedDeviceId);
                throw new AccessDeniedException("Access Denied");
            }
        }
        return message;
    }
}

