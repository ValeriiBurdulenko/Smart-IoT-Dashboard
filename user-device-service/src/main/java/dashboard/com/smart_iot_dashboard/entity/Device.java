package dashboard.com.smart_iot_dashboard.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String deviceId;

    @Column(nullable = false)
    private String hashedDeviceToken;

    @Column(nullable = false, length = 100)
    private String userId;

    @Column(length = 100)
    private String name;

    @Column(length = 255)
    private String location;

    // @Column(updatable = false)
    // private Instant createdAt;
    // private Instant updatedAt;
    //
    // @PrePersist
    // protected void onCreate() { createdAt = Instant.now(); }
    // @PreUpdate
    // protected void onUpdate() { updatedAt = Instant.now(); }
}
