package ru.syncroom.push.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.syncroom.users.domain.User;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"endpoint"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "endpoint", nullable = false, columnDefinition = "text")
    private String endpoint;

    @Column(name = "p256dh", nullable = false, columnDefinition = "text")
    private String p256dh;

    @Column(name = "auth", nullable = false, columnDefinition = "text")
    private String auth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
