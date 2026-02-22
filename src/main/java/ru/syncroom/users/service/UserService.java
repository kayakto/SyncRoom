package ru.syncroom.users.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.dto.ProfileResponse;
import ru.syncroom.users.dto.UpdateProfileRequest;
import ru.syncroom.users.repository.UserRepository;

import java.util.UUID;

/**
 * Service for user profile operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Gets user profile by ID.
     * 
     * @param userId User ID
     * @return User profile
     * @throws NotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        return ProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .provider(user.getProvider().getValue())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    /**
     * Updates user profile.
     * 
     * @param userId User ID
     * @param request Update request
     * @return Updated user profile
     * @throws NotFoundException if user not found
     * @throws BadRequestException if email already exists (for another user)
     */
    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        
        // Проверяем уникальность email, если он изменился
        if (!user.getEmail().equals(request.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email already exists");
            }
        }
        
        // Обновляем поля
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setAvatarUrl(request.getAvatarUrl());
        
        user = userRepository.save(user);
        
        log.debug("Updated profile for user {}", userId);
        
        return ProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .provider(user.getProvider().getValue())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
