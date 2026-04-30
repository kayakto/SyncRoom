package ru.syncroom.push.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.push.config.PushProperties;
import ru.syncroom.push.domain.PushSubscription;
import ru.syncroom.push.dto.PushSubscribeRequest;
import ru.syncroom.push.dto.PushSubscriptionResponse;
import ru.syncroom.push.dto.PushUnsubscribeRequest;
import ru.syncroom.push.repository.PushSubscriptionRepository;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushService {

    private final PushProperties pushProperties;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public String getVapidPublicKey() {
        return pushProperties.getVapidPublicKey();
    }

    @Transactional
    public PushSubscriptionResponse subscribe(UUID userId, PushSubscribeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        PushSubscription subscription = pushSubscriptionRepository.findByEndpoint(request.getEndpoint())
                .orElseGet(PushSubscription::new);

        subscription.setUser(user);
        subscription.setEndpoint(request.getEndpoint());
        subscription.setP256dh(request.getKeys().getP256dh());
        subscription.setAuth(request.getKeys().getAuth());
        pushSubscriptionRepository.save(subscription);

        return PushSubscriptionResponse.builder()
                .endpoint(request.getEndpoint())
                .subscribed(true)
                .build();
    }

    @Transactional
    public PushSubscriptionResponse unsubscribe(UUID userId, PushUnsubscribeRequest request) {
        pushSubscriptionRepository.deleteByEndpointAndUser_Id(request.getEndpoint(), userId);
        return PushSubscriptionResponse.builder()
                .endpoint(request.getEndpoint())
                .subscribed(false)
                .build();
    }
}
