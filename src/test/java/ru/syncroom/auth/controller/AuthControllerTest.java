package ru.syncroom.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.syncroom.auth.client.ExternalOAuthClient;
import ru.syncroom.auth.dto.*;
import ru.syncroom.auth.service.AuthService;
import ru.syncroom.common.security.JwtTokenService;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для AuthController.
 * Тестирует все endpoints аутентификации.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Auth Controller Integration Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private ExternalOAuthClient externalOAuthClient;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_NAME = "Test User";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/auth/register - успешная регистрация")
    void testRegister_Success() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);
        request.setName(TEST_NAME);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.isFirstLogin").value(true))
                .andExpect(jsonPath("$.user.id").exists())
                .andExpect(jsonPath("$.user.name").value(TEST_NAME))
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.user.provider").value("email"));

        // Проверяем, что пользователь создан в БД
        assert userRepository.findByEmail(TEST_EMAIL).isPresent();
    }

    @Test
    @DisplayName("POST /api/auth/register - ошибка: email уже существует")
    void testRegister_EmailAlreadyExists() throws Exception {
        // Given - создаем пользователя заранее
        User existingUser = User.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build();
        userRepository.save(existingUser);

        RegisterRequest request = new RegisterRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);
        request.setName(TEST_NAME);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Email already exists")));
    }

    @Test
    @DisplayName("POST /api/auth/register - ошибка: валидация (короткий пароль)")
    void testRegister_ValidationError_ShortPassword() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("12345"); // Пароль < 6 символов
        request.setName(TEST_NAME);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register - ошибка: валидация (неверный email)")
    void testRegister_ValidationError_InvalidEmail() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("invalid-email");
        request.setPassword(TEST_PASSWORD);
        request.setName(TEST_NAME);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("POST /api/auth/email - успешная аутентификация")
    void testEmailAuth_Success() throws Exception {
        // Given - создаем пользователя
        String hashedPassword = passwordEncoder.encode(TEST_PASSWORD);
        User user = User.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .provider(AuthProvider.EMAIL)
                .passwordHash(hashedPassword)
                .createdAt(OffsetDateTime.now())
                .build();
        userRepository.save(user);

        EmailAuthRequest request = new EmailAuthRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL));
    }

    @Test
    @DisplayName("POST /api/auth/email - ошибка: неверный пароль")
    void testEmailAuth_WrongPassword() throws Exception {
        // Given - создаем пользователя
        String hashedPassword = passwordEncoder.encode(TEST_PASSWORD);
        User user = User.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .provider(AuthProvider.EMAIL)
                .passwordHash(hashedPassword)
                .createdAt(OffsetDateTime.now())
                .build();
        userRepository.save(user);

        EmailAuthRequest request = new EmailAuthRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("wrongpassword");
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Invalid email or password")));
    }

    @Test
    @DisplayName("POST /api/auth/email - ошибка: пользователь не найден")
    void testEmailAuth_UserNotFound() throws Exception {
        // Given
        EmailAuthRequest request = new EmailAuthRequest();
        request.setEmail("nonexistent@example.com");
        request.setPassword(TEST_PASSWORD);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Invalid email or password")));
    }

    @Test
    @DisplayName("POST /api/auth/oauth - успешная аутентификация через VK (новый пользователь)")
    void testOAuth_Success_NewUser() throws Exception {
        // Given
        String vkAccessToken = "test_vk_token_123";
        ExternalOAuthClient.OAuthUserProfile mockProfile = ExternalOAuthClient.OAuthUserProfile.builder()
                .id("vk_user_123")
                .name("VK User")
                .email("vkuser@example.com")
                .avatarUrl("https://vk.com/avatar.jpg")
                .build();

        when(externalOAuthClient.getUserProfile(eq(AuthProvider.VK), eq(vkAccessToken)))
                .thenReturn(mockProfile);

        OAuthRequest request = new OAuthRequest();
        request.setProvider("vk");
        request.setAccessToken(vkAccessToken);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/oauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.isFirstLogin").value(true))
                .andExpect(jsonPath("$.user.name").value("VK User"))
                .andExpect(jsonPath("$.user.email").value("vkuser@example.com"))
                .andExpect(jsonPath("$.user.provider").value("vk"))
                .andExpect(jsonPath("$.user.avatarUrl").value("https://vk.com/avatar.jpg"));

        // Проверяем, что пользователь создан
        assert userRepository.findByProviderAndProviderId(AuthProvider.VK, "vk_user_123").isPresent();
    }

    @Test
    @DisplayName("POST /api/auth/oauth - успешная аутентификация через VK (существующий пользователь)")
    void testOAuth_Success_ExistingUser() throws Exception {
        // Given - создаем пользователя заранее
        User existingUser = User.builder()
                .name("Existing VK User")
                .email("existing@example.com")
                .provider(AuthProvider.VK)
                .providerId("vk_user_123")
                .createdAt(OffsetDateTime.now().minusDays(1))
                .build();
        userRepository.save(existingUser);

        String vkAccessToken = "test_vk_token_123";
        ExternalOAuthClient.OAuthUserProfile mockProfile = ExternalOAuthClient.OAuthUserProfile.builder()
                .id("vk_user_123")
                .name("Updated VK User")
                .email("updated@example.com")
                .avatarUrl("https://vk.com/new_avatar.jpg")
                .build();

        when(externalOAuthClient.getUserProfile(eq(AuthProvider.VK), eq(vkAccessToken)))
                .thenReturn(mockProfile);

        OAuthRequest request = new OAuthRequest();
        request.setProvider("vk");
        request.setAccessToken(vkAccessToken);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/oauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFirstLogin").value(false))
                .andExpect(jsonPath("$.user.id").value(existingUser.getId().toString()));
    }

    @Test
    @DisplayName("POST /api/auth/oauth - успешная аутентификация через Yandex (новый пользователь)")
    void testOAuth_Success_Yandex_NewUser() throws Exception {
        // Given
        String yandexAccessToken = "test_yandex_token_123";
        ExternalOAuthClient.OAuthUserProfile mockProfile = ExternalOAuthClient.OAuthUserProfile.builder()
                .id("yandex_user_123")
                .name("Yandex User")
                .email("yandexuser@yandex.ru")
                .avatarUrl("https://avatars.yandex.net/get-yapic/123/islands-200")
                .build();

        when(externalOAuthClient.getUserProfile(eq(AuthProvider.YANDEX), eq(yandexAccessToken)))
                .thenReturn(mockProfile);

        OAuthRequest request = new OAuthRequest();
        request.setProvider("yandex");
        request.setAccessToken(yandexAccessToken);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/oauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.isFirstLogin").value(true))
                .andExpect(jsonPath("$.user.name").value("Yandex User"))
                .andExpect(jsonPath("$.user.email").value("yandexuser@yandex.ru"))
                .andExpect(jsonPath("$.user.provider").value("yandex"))
                .andExpect(jsonPath("$.user.avatarUrl").value("https://avatars.yandex.net/get-yapic/123/islands-200"));

        // Проверяем, что пользователь создан
        assert userRepository.findByProviderAndProviderId(AuthProvider.YANDEX, "yandex_user_123").isPresent();
    }

    @Test
    @DisplayName("POST /api/auth/oauth - ошибка: неверный провайдер")
    void testOAuth_InvalidProvider() throws Exception {
        // Given
        OAuthRequest request = new OAuthRequest();
        request.setProvider("invalid_provider");
        request.setAccessToken("token");
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/oauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists()); // IllegalArgumentException или BadRequestException
    }

    @Test
    @DisplayName("POST /api/auth/oauth - ошибка: неверный токен VK")
    void testOAuth_InvalidToken() throws Exception {
        // Given
        String vkAccessToken = "invalid_token";
        when(externalOAuthClient.getUserProfile(eq(AuthProvider.VK), eq(vkAccessToken)))
                .thenThrow(new ru.syncroom.common.exception.UnauthorizedException("Invalid VK access token"));

        OAuthRequest request = new OAuthRequest();
        request.setProvider("vk");
        request.setAccessToken(vkAccessToken);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/oauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Invalid VK access token")));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - успешное обновление токенов")
    void testRefresh_Success() throws Exception {
        // Given - создаем пользователя и генерируем refresh токен
        User user = User.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .provider(AuthProvider.EMAIL)
                .createdAt(OffsetDateTime.now())
                .build();
        user = userRepository.save(user);

        String refreshToken = jwtTokenService.generateRefreshToken(user.getId());

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(refreshToken);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
                // Примечание: новые токены всегда отличаются от старых, но проверка на равенство может быть нестабильной
    }

    @Test
    @DisplayName("POST /api/auth/refresh - ошибка: неверный refresh токен")
    void testRefresh_InvalidToken() throws Exception {
        // Given
        String invalidToken = "invalid.refresh.token";
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(invalidToken);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/auth/refresh - ошибка: access токен вместо refresh токена")
    void testRefresh_AccessTokenInsteadOfRefresh() throws Exception {
        // Given - создаем пользователя и генерируем access токен (не refresh)
        User user = User.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .provider(AuthProvider.EMAIL)
                .createdAt(OffsetDateTime.now())
                .build();
        user = userRepository.save(user);

        String accessToken = jwtTokenService.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(), user.getProvider().getValue()
        );

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(accessToken);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Invalid refresh token")));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - ошибка: пользователь не найден")
    void testRefresh_UserNotFound() throws Exception {
        // Given - создаем валидный refresh токен для несуществующего пользователя
        UUID nonExistentUserId = UUID.randomUUID();
        String refreshToken = jwtTokenService.generateRefreshToken(nonExistentUserId);

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(refreshToken);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("User not found")));
    }
}
