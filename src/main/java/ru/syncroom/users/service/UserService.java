package ru.syncroom.users.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.study.domain.StudyTask;
import ru.syncroom.study.repository.StudyTaskRepository;
import ru.syncroom.study.repository.TaskLikeRepository;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.dto.CompletedGoalResponse;
import ru.syncroom.users.dto.ProfileResponse;
import ru.syncroom.users.dto.UpdateProfileRequest;
import ru.syncroom.users.dto.UserStatsResponse;
import ru.syncroom.users.repository.UserRepository;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

/**
 * Service for user profile operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StudyTaskRepository studyTaskRepository;
    private final TaskLikeRepository taskLikeRepository;
    private final AvatarStorage avatarStorage;

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
        
        // Обновляем поля профиля. Аватар обновляется через отдельный upload endpoint.
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        
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

    /**
     * Загрузка нового аватара (PNG/JPEG/WebP) в хранилище; в профиль записывается публичный URL (API или CDN).
     */
    @Transactional
    public ProfileResponse uploadAvatar(UUID userId, MultipartFile file) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Empty file");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String url = avatarStorage.saveAndPublicUrl(userId, file.getBytes(), file.getContentType());
        user.setAvatarUrl(url);
        userRepository.save(user);
        log.debug("Updated avatar for user {}", userId);
        return getProfile(userId);
    }

    @Transactional(readOnly = true)
    public UserStatsResponse getStats(UUID userId) {
        // keep same behavior as profile endpoint: 404 if user doesn't exist
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime dayStart = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        OffsetDateTime weekStart = dayStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        long completedToday = studyTaskRepository.countByUser_IdAndIsDoneTrueAndUpdatedAtGreaterThanEqual(userId, dayStart);
        long completedThisWeek = studyTaskRepository.countByUser_IdAndIsDoneTrueAndUpdatedAtGreaterThanEqual(userId, weekStart);
        long completedTotal = studyTaskRepository.countByUser_IdAndIsDoneTrue(userId);
        long totalLikesReceived = taskLikeRepository.countLikesOnTasksOwnedByUser(userId);

        List<CompletedGoalResponse> recentCompletedGoals = studyTaskRepository
                .findTop20ByUser_IdAndIsDoneTrueOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toCompletedGoalResponse)
                .toList();

        return UserStatsResponse.builder()
                .completedToday(completedToday)
                .completedThisWeek(completedThisWeek)
                .completedTotal(completedTotal)
                .totalLikesReceived(totalLikesReceived)
                .recentCompletedGoals(recentCompletedGoals)
                .build();
    }

    private CompletedGoalResponse toCompletedGoalResponse(StudyTask task) {
        return CompletedGoalResponse.builder()
                .id(task.getId())
                .text(task.getText())
                .roomId(task.getRoom().getId())
                .roomTitle(task.getRoom().getTitle())
                .completedAt(task.getUpdatedAt())
                .build();
    }
}
