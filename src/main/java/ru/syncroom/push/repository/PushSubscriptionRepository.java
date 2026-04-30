package ru.syncroom.push.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.syncroom.push.domain.PushSubscription;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {
    Optional<PushSubscription> findByEndpoint(String endpoint);
    long deleteByEndpointAndUser_Id(String endpoint, UUID userId);
}
