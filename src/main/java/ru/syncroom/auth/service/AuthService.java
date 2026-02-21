package ru.syncroom.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.auth.client.ExternalOAuthClient;
import ru.syncroom.auth.dto.AuthResponse;
import ru.syncroom.auth.dto.RefreshResponse;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.common.exception.UnauthorizedException;
import ru.syncroom.common.security.JwtTokenService;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service for authentication operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final ExternalOAuthClient externalOAuthClient;

    /**
     * Authenticates user via OAuth provider (VK or Yandex).
     */
    @Transactional
    public AuthResponse authenticateOAuth(String providerStr, String accessToken) {
        AuthProvider provider = AuthProvider.fromString(providerStr);
        
        if (provider != AuthProvider.VK && provider != AuthProvider.YANDEX) {
            throw new BadRequestException("OAuth provider must be 'vk' or 'yandex'");
        }

        // Fetch user profile from external provider
        ExternalOAuthClient.OAuthUserProfile profile = externalOAuthClient.getUserProfile(provider, accessToken);
        
        // Find or create user
        boolean isFirstLogin;
        User user = userRepository.findByProviderAndProviderId(provider, profile.getId())
                .orElseGet(() -> {
                    log.info("Creating new user for provider {} with id {}", provider, profile.getId());
                    return createOAuthUser(provider, profile);
                });

        // Check if user was just created (within last minute)
        isFirstLogin = user.getCreatedAt().isAfter(OffsetDateTime.now().minusMinutes(1));

        // Generate tokens
        String jwtAccessToken = jwtTokenService.generateAccessToken(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProvider().getValue()
        );
        String jwtRefreshToken = jwtTokenService.generateRefreshToken(user.getId());

        return buildAuthResponse(jwtAccessToken, jwtRefreshToken, isFirstLogin, user);
    }

    /**
     * Authenticates user via email/password.
     */
    @Transactional
    public AuthResponse authenticateEmail(String email, String password) {
        User user = userRepository.findByEmailAndProvider(email, AuthProvider.EMAIL)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String jwtAccessToken = jwtTokenService.generateAccessToken(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProvider().getValue()
        );
        String jwtRefreshToken = jwtTokenService.generateRefreshToken(user.getId());

        return buildAuthResponse(jwtAccessToken, jwtRefreshToken, false, user);
    }

    /**
     * Registers a new user with email/password.
     */
    @Transactional
    public AuthResponse register(String email, String password, String name) {
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already exists");
        }

        User user = User.builder()
                .name(name)
                .email(email)
                .provider(AuthProvider.EMAIL)
                .passwordHash(passwordEncoder.encode(password))
                .createdAt(OffsetDateTime.now())
                .build();

        user = userRepository.save(user);

        String jwtAccessToken = jwtTokenService.generateAccessToken(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProvider().getValue()
        );
        String jwtRefreshToken = jwtTokenService.generateRefreshToken(user.getId());

        return buildAuthResponse(jwtAccessToken, jwtRefreshToken, true, user);
    }

    /**
     * Refreshes access token using refresh token.
     */
    @Transactional
    public RefreshResponse refresh(String refreshToken) {
        if (!jwtTokenService.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        UUID userId = jwtTokenService.validateTokenAndGetUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String newAccessToken = jwtTokenService.generateAccessToken(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProvider().getValue()
        );
        String newRefreshToken = jwtTokenService.generateRefreshToken(user.getId());

        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    private User createOAuthUser(AuthProvider provider, ExternalOAuthClient.OAuthUserProfile profile) {
        User user = User.builder()
                .name(profile.getName())
                .email(profile.getEmail())
                .provider(provider)
                .providerId(profile.getId())
                .avatarUrl(profile.getAvatarUrl())
                .createdAt(OffsetDateTime.now())
                .build();
        return userRepository.save(user);
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, boolean isFirstLogin, User user) {
        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .provider(user.getProvider().getValue())
                .avatarUrl(user.getAvatarUrl())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .isFirstLogin(isFirstLogin)
                .user(userDto)
                .build();
    }
}
