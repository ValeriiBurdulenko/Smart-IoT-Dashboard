
package dashboard.com.smart_iot_dashboard.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${keycloak.webhook.auth.username}")
    private String webhookUsername;

    @Value("${keycloak.webhook.auth.password}")
    private String webhookPassword;

    // --- 1. Chain for INTERNAL APIs (MQTT Auth, Webhook) ---
    @Bean
    @Order(1)
    public SecurityFilterChain internalApiSecurityChain(HttpSecurity http, UserDetailsService internalUserDetailsService) throws Exception {
        http
                .securityMatcher("/api/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/internal/mqtt/auth", "/api/internal/mqtt/acl").permitAll()
                        .requestMatchers("/api/internal/keycloak-events").authenticated()
                        .anyRequest().denyAll()
                )
                .httpBasic(withDefaults())
                .userDetailsService(internalUserDetailsService);

        return http.build();
    }

    @Bean
    public UserDetailsService internalUserDetailsService(PasswordEncoder passwordEncoderInternal) {
        UserDetails user = User.builder()
                .username(webhookUsername)
                .password(passwordEncoderInternal.encode(webhookPassword))
                .roles("INTERNAL_API")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    // --- 2. Chain for USER APIs (Keycloak JWT) ---

    @Bean
    @Order(2)
    public SecurityFilterChain userApiSecurityChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/v1/devices/claim-with-code").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoderInternal() {
        return new BCryptPasswordEncoder();
    }
}