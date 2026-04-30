package ru.syncroom.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.security.JwtTokenService;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.study.domain.StudyTask;
import ru.syncroom.study.domain.TaskLike;
import ru.syncroom.study.repository.StudyTaskRepository;
import ru.syncroom.study.repository.TaskLikeRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для UserController.
 * Тестирует endpoints для работы с профилем пользователя.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("User Controller Integration Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StudyTaskRepository studyTaskRepository;

    @Autowired
    private TaskLikeRepository taskLikeRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_NAME = "Test User";
    private static final String TEST_PASSWORD = "password123";

    private User testUser;
    private String accessToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        
        // Создаем тестового пользователя
        testUser = User.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build();
        testUser = userRepository.save(testUser);
        
        // Генерируем JWT токен для тестового пользователя
        accessToken = jwtTokenService.generateAccessToken(
                testUser.getId(),
                testUser.getName(),
                testUser.getEmail(),
                testUser.getProvider().getValue()
        );
    }
    
    private String getAuthHeader() {
        return "Bearer " + accessToken;
    }

    @Test
    @DisplayName("GET /api/users/me - успешное получение профиля")
    void testGetCurrentProfile_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", getAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.name").value(TEST_NAME))
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.provider").value("email"));
    }

    @Test
    @DisplayName("GET /api/users/me - ошибка: не авторизован")
    void testGetCurrentProfile_Unauthorized() throws Exception {
        // When & Then - без заголовка Authorization
        mockMvc.perform(get("/api/users/me"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403 : "Expected 401 or 403, got " + status;
                });
    }

    @Test
    @DisplayName("PUT /api/users/me - успешное обновление профиля")
    void testUpdateProfile_Success() throws Exception {
        // Given
        ru.syncroom.users.dto.UpdateProfileRequest request = new ru.syncroom.users.dto.UpdateProfileRequest();
        request.setName("Updated Name");
        request.setEmail("updated@example.com");
        request.setAvatarUrl("https://example.com/new-avatar.jpg");
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", getAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/new-avatar.jpg"));

        // Проверяем, что данные обновились в БД
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assert updatedUser.getName().equals("Updated Name");
        assert updatedUser.getEmail().equals("updated@example.com");
    }

    @Test
    @DisplayName("PUT /api/users/me - успешное обновление только имени")
    void testUpdateProfile_Success_NameOnly() throws Exception {
        // Given
        ru.syncroom.users.dto.UpdateProfileRequest request = new ru.syncroom.users.dto.UpdateProfileRequest();
        request.setName("New Name");
        request.setEmail(TEST_EMAIL); // Оставляем тот же email
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", getAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.email").value(TEST_EMAIL));
    }

    @Test
    @DisplayName("PUT /api/users/me - ошибка: email уже существует")
    void testUpdateProfile_EmailAlreadyExists() throws Exception {
        // Given - создаем другого пользователя
        User otherUser = User.builder()
                .name("Other User")
                .email("other@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build();
        userRepository.save(otherUser);

        ru.syncroom.users.dto.UpdateProfileRequest request = new ru.syncroom.users.dto.UpdateProfileRequest();
        request.setName(TEST_NAME);
        request.setEmail("other@example.com"); // Email другого пользователя
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", getAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Email already exists")));
    }

    @Test
    @DisplayName("PUT /api/users/me - ошибка: валидация (пустое имя)")
    void testUpdateProfile_ValidationError_EmptyName() throws Exception {
        // Given
        ru.syncroom.users.dto.UpdateProfileRequest request = new ru.syncroom.users.dto.UpdateProfileRequest();
        request.setName(""); // Пустое имя
        request.setEmail(TEST_EMAIL);
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", getAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("PUT /api/users/me - ошибка: валидация (неверный email)")
    void testUpdateProfile_ValidationError_InvalidEmail() throws Exception {
        // Given
        ru.syncroom.users.dto.UpdateProfileRequest request = new ru.syncroom.users.dto.UpdateProfileRequest();
        request.setName(TEST_NAME);
        request.setEmail("invalid-email"); // Неверный формат email
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", getAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("PUT /api/users/me - ошибка: не авторизован")
    void testUpdateProfile_Unauthorized() throws Exception {
        // Given
        ru.syncroom.users.dto.UpdateProfileRequest request = new ru.syncroom.users.dto.UpdateProfileRequest();
        request.setName("New Name");
        request.setEmail("new@example.com");
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then - без заголовка Authorization
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403 : "Expected 401 or 403, got " + status;
                });
    }

    @Test
    @DisplayName("PUT /api/users/me - успешное обновление с null аватаром")
    void testUpdateProfile_Success_NullAvatar() throws Exception {
        // Given - пользователь с аватаром
        testUser.setAvatarUrl("https://example.com/old-avatar.jpg");
        userRepository.save(testUser);

        ru.syncroom.users.dto.UpdateProfileRequest request = new ru.syncroom.users.dto.UpdateProfileRequest();
        request.setName(TEST_NAME);
        request.setEmail(TEST_EMAIL);
        request.setAvatarUrl(null); // Удаляем аватар
        String requestBody = objectMapper.writeValueAsString(request);

        // When & Then
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", getAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").isEmpty());
    }

    @Test
    @DisplayName("GET /api/users/me/stats - успешное получение статистики")
    void testGetCurrentStats_Success() throws Exception {
        Room room = roomRepository.save(Room.builder()
                .context("study")
                .title("Study Room")
                .maxParticipants(10)
                .isActive(true)
                .build());

        StudyTask doneToday1 = studyTaskRepository.save(StudyTask.builder()
                .user(testUser)
                .room(room)
                .text("Task done today 1")
                .isDone(true)
                .sortOrder(0)
                .build());
        StudyTask doneToday2 = studyTaskRepository.save(StudyTask.builder()
                .user(testUser)
                .room(room)
                .text("Task done today 2")
                .isDone(true)
                .sortOrder(1)
                .build());
        StudyTask inProgress = studyTaskRepository.save(StudyTask.builder()
                .user(testUser)
                .room(room)
                .text("Task not done")
                .isDone(false)
                .sortOrder(2)
                .build());

        User liker = userRepository.save(User.builder()
                .name("Liker User")
                .email("liker@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());

        taskLikeRepository.save(TaskLike.builder().task(doneToday1).user(liker).build());
        taskLikeRepository.save(TaskLike.builder().task(doneToday2).user(liker).build());

        mockMvc.perform(get("/api/users/me/stats")
                        .header("Authorization", getAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.completedToday").value(2))
                .andExpect(jsonPath("$.completedThisWeek").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.completedTotal").value(2))
                .andExpect(jsonPath("$.totalLikesReceived").value(2))
                .andExpect(jsonPath("$.recentCompletedGoals", hasSize(2)))
                .andExpect(jsonPath("$.recentCompletedGoals[0].text", anyOf(
                        is("Task done today 1"), is("Task done today 2")
                )))
                .andExpect(jsonPath("$.recentCompletedGoals[1].text", anyOf(
                        is("Task done today 1"), is("Task done today 2")
                )))
                .andExpect(jsonPath("$.recentCompletedGoals[*].text", not(hasItem("Task not done"))));
    }
}
