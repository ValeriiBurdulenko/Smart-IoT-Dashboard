package dashboard.com.smart_iot_dashboard.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_view_history",
        indexes = {
                // findTopDevicesSummaries()
                @Index(
                        name = "idx_view_user_recent",
                        columnList = "user_id, viewed_at DESC, device_id"
                ),
                // hasRecentView()
                @Index(
                        name = "idx_view_device_recent",
                        columnList = "user_id, device_id, viewed_at DESC"
                ),
                // deleteHistoryOlderThan()
                @Index(
                        name = "idx_view_cleanup",
                        columnList = "viewed_at"
                )
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceViewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId; // ID user from Keycloak

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @CreationTimestamp
    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}
