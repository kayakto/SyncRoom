package ru.syncroom.points.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.security.JwtTokenService;
import ru.syncroom.points.domain.Point;
import ru.syncroom.points.dto.CreatePointRequest;
import ru.syncroom.points.repository.PointRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для PointController.
 * Тестирует все CRUD эндпоинты для сохранённых мест пользователя.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Point Controller Integration Tests")
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private static final String TEST_EMAIL = "pointsuser@example.com";
    private static final String TEST_NAME = "Points Test User";

    private User testUser;
    private String accessToken;

    @BeforeEach
    void setUp() {
        pointRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build();
        testUser = userRepository.save(testUser);

        accessToken = jwtTokenService.generateAccessToken(
                testUser.getId(),
                testUser.getName(),
                testUser.getEmail(),
                testUser.getProvider().getValue());
    }

    private String authHeader() {
        return "Bearer " + accessToken;
    }

    /** Вспомогательный метод: создать Point в БД напрямую */
    private Point createPoint(String context, String title, String address) {
        Point point = Point.builder()
                .user(testUser)
                .context(context)
                .title(title)
                .address(address)
                .latitude(55.751244)
                .longitude(37.618423)
                .build();
        return pointRepository.save(point);
    }

    /** Вспомогательный метод: создать валидный CreatePointRequest */
    private CreatePointRequest validRequest(String context) {
        CreatePointRequest req = new CreatePointRequest();
        req.setContext(context);
        req.setTitle("Дом");
        req.setAddress("Москва, ул. Пушкина, д. 10");
        req.setLatitude(55.751244);
        req.setLongitude(37.618423);
        return req;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/users/{userId}/points
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users/{userId}/points - успешно возвращает пустой список")
    void testGetPoints_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/points", testUser.getId())
                .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/users/{userId}/points - успешно возвращает список точек")
    void testGetPoints_ReturnsList() throws Exception {
        // Given
        createPoint("leisure", "Дом", "Москва, ул. Пушкина, д. 10");
        createPoint("work", "Работа", "Москва, Тверская ул., 1");

        // When & Then
        mockMvc.perform(get("/api/users/{userId}/points", testUser.getId())
                .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].context", containsInAnyOrder("leisure", "work")))
                .andExpect(jsonPath("$[*].userId",
                        everyItem(is(testUser.getId().toString()))));
    }

    @Test
    @DisplayName("GET /api/users/{userId}/points - структура PointResponse корректна")
    void testGetPoints_ResponseStructure() throws Exception {
        // Given
        createPoint("sport", "Спортзал", "Москва, Ленинский пр., 5");

        // When & Then
        mockMvc.perform(get("/api/users/{userId}/points", testUser.getId())
                .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].userId").value(testUser.getId().toString()))
                .andExpect(jsonPath("$[0].context").value("sport"))
                .andExpect(jsonPath("$[0].title").value("Спортзал"))
                .andExpect(jsonPath("$[0].address").value("Москва, Ленинский пр., 5"))
                .andExpect(jsonPath("$[0].latitude").value(55.751244))
                .andExpect(jsonPath("$[0].longitude").value(37.618423));
    }

    @Test
    @DisplayName("GET /api/users/{userId}/points - ошибка: не авторизован")
    void testGetPoints_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/points", testUser.getId()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    @DisplayName("GET /api/users/{userId}/points - ошибка: неверный Bearer токен")
    void testGetPoints_InvalidToken() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/points", testUser.getId())
                .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/users/{userId}/points
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/users/{userId}/points - успешное создание точки (leisure)")
    void testCreatePoint_Success_Leisure() throws Exception {
        // Given
        CreatePointRequest req = validRequest("leisure");

        // When & Then
        mockMvc.perform(post("/api/users/{userId}/points", testUser.getId())
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.context").value("leisure"))
                .andExpect(jsonPath("$.title").value("Дом"))
                .andExpect(jsonPath("$.address").value("Москва, ул. Пушкина, д. 10"))
                .andExpect(jsonPath("$.latitude").value(55.751244))
                .andExpect(jsonPath("$.longitude").value(37.618423));

        // Проверяем, что запись создалась в БД
        assertEquals(1, pointRepository.findByUserId(testUser.getId()).size());
    }

    @Test
    @DisplayName("POST /api/users/{userId}/points - успешное создание всех контекстов")
    void testCreatePoint_AllContexts() throws Exception {
        for (String ctx : new String[] { "work", "study", "sport", "leisure" }) {
            CreatePointRequest req = validRequest(ctx);
            mockMvc.perform(post("/api/users/{userId}/points", testUser.getId())
                    .header("Authorization", authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.context").value(ctx));
        }
        assertEquals(4, pointRepository.findByUserId(testUser.getId()).size());
    }

    @Test
    @DisplayName("POST /api/users/{userId}/points - контекст нормализуется в нижний регистр")
    void testCreatePoint_ContextNormalized() throws Exception {
        // Given - uppercase context
        CreatePointRequest req = validRequest("WORK");

        // When & Then
        mockMvc.perform(post("/api/users/{userId}/points", testUser.getId())
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context").value("work"));
    }

    @Test
    @DisplayName("POST /api/users/{userId}/points - ошибка: неверный context")
    void testCreatePoint_InvalidContext() throws Exception {
        // Given
        CreatePointRequest req = validRequest("invalid_context");

        // When & Then
        mockMvc.perform(post("/api/users/{userId}/points", testUser.getId())
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid context")));
    }

    @Test
    @DisplayName("POST /api/users/{userId}/points - ошибка: валидация (пустой title)")
    void testCreatePoint_ValidationError_EmptyTitle() throws Exception {
        // Given
        CreatePointRequest req = validRequest("work");
        req.setTitle("");

        // When & Then
        mockMvc.perform(post("/api/users/{userId}/points", testUser.getId())
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    @DisplayName("POST /api/users/{userId}/points - ошибка: валидация (null latitude)")
    void testCreatePoint_ValidationError_NullLatitude() throws Exception {
        // Given
        CreatePointRequest req = validRequest("work");
        req.setLatitude(null);

        // When & Then
        mockMvc.perform(post("/api/users/{userId}/points", testUser.getId())
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latitude").exists());
    }

    @Test
    @DisplayName("POST /api/users/{userId}/points - ошибка: валидация (пустой address)")
    void testCreatePoint_ValidationError_EmptyAddress() throws Exception {
        // Given
        CreatePointRequest req = validRequest("sport");
        req.setAddress("");

        // When & Then
        mockMvc.perform(post("/api/users/{userId}/points", testUser.getId())
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.address").exists());
    }

    @Test
    @DisplayName("POST /api/users/{userId}/points - ошибка: не авторизован")
    void testCreatePoint_Unauthorized() throws Exception {
        CreatePointRequest req = validRequest("work");

        mockMvc.perform(post("/api/users/{userId}/points", testUser.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    @DisplayName("POST /api/users/{userId}/points - ошибка: пользователь не найден")
    void testCreatePoint_UserNotFound() throws Exception {
        UUID nonExistentUserId = UUID.randomUUID();
        CreatePointRequest req = validRequest("work");

        mockMvc.perform(post("/api/users/{userId}/points", nonExistentUserId)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("User not found")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/users/{userId}/points/{pointId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/users/{userId}/points/{pointId} - успешное обновление")
    void testUpdatePoint_Success() throws Exception {
        // Given
        Point existing = createPoint("leisure", "Дом", "Москва, ул. Пушкина, д. 10");

        CreatePointRequest req = new CreatePointRequest();
        req.setContext("work");
        req.setTitle("Офис");
        req.setAddress("Москва, Новый Арбат, 15");
        req.setLatitude(55.752428);
        req.setLongitude(37.587614);

        // When & Then
        mockMvc.perform(put("/api/users/{userId}/points/{pointId}",
                testUser.getId(), existing.getId())
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.getId().toString()))
                .andExpect(jsonPath("$.context").value("work"))
                .andExpect(jsonPath("$.title").value("Офис"))
                .andExpect(jsonPath("$.address").value("Москва, Новый Арбат, 15"))
                .andExpect(jsonPath("$.latitude").value(55.752428))
                .andExpect(jsonPath("$.longitude").value(37.587614));

        // Проверяем что данные обновились в БД
        Point updated = pointRepository.findById(existing.getId()).orElseThrow();
        assertEquals("work", updated.getContext());
        assertEquals("Офис", updated.getTitle());
    }

    @Test
    @DisplayName("PUT /api/users/{userId}/points/{pointId} - ошибка: точка не найдена")
    void testUpdatePoint_NotFound() throws Exception {
        UUID nonExistentPointId = UUID.randomUUID();
        CreatePointRequest req = validRequest("work");

        mockMvc.perform(put("/api/users/{userId}/points/{pointId}",
                testUser.getId(), nonExistentPointId)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Point not found")));
    }

    @Test
    @DisplayName("PUT /api/users/{userId}/points/{pointId} - ошибка: чужая точка (неверный userId)")
    void testUpdatePoint_WrongUser() throws Exception {
        // Given - точка принадлежит testUser, но обновляем от другого userId
        Point existing = createPoint("leisure", "Дом", "Адрес");
        UUID anotherUserId = UUID.randomUUID();
        CreatePointRequest req = validRequest("work");

        // When & Then - точка не найдена для другого userId → 404
        mockMvc.perform(put("/api/users/{userId}/points/{pointId}",
                anotherUserId, existing.getId())
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/users/{userId}/points/{pointId} - ошибка: неверный context")
    void testUpdatePoint_InvalidContext() throws Exception {
        // Given
        Point existing = createPoint("leisure", "Дом", "Адрес");
        CreatePointRequest req = validRequest("wrong");

        // When & Then
        mockMvc.perform(put("/api/users/{userId}/points/{pointId}",
                testUser.getId(), existing.getId())
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid context")));
    }

    @Test
    @DisplayName("PUT /api/users/{userId}/points/{pointId} - ошибка: не авторизован")
    void testUpdatePoint_Unauthorized() throws Exception {
        Point existing = createPoint("leisure", "Дом", "Адрес");
        CreatePointRequest req = validRequest("work");

        mockMvc.perform(put("/api/users/{userId}/points/{pointId}",
                testUser.getId(), existing.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/users/{userId}/points/{pointId}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/users/{userId}/points/{pointId} - успешное удаление")
    void testDeletePoint_Success() throws Exception {
        // Given
        Point existing = createPoint("work", "Работа", "Москва, Тверская, 1");

        // When & Then
        mockMvc.perform(delete("/api/users/{userId}/points/{pointId}",
                testUser.getId(), existing.getId())
                .header("Authorization", authHeader()))
                .andExpect(status().isNoContent());

        // Проверяем что запись удалена из БД
        assertFalse(pointRepository.findById(existing.getId()).isPresent());
    }

    @Test
    @DisplayName("DELETE /api/users/{userId}/points/{pointId} - ошибка: точка не найдена")
    void testDeletePoint_NotFound() throws Exception {
        UUID nonExistentPointId = UUID.randomUUID();

        mockMvc.perform(delete("/api/users/{userId}/points/{pointId}",
                testUser.getId(), nonExistentPointId)
                .header("Authorization", authHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Point not found")));
    }

    @Test
    @DisplayName("DELETE /api/users/{userId}/points/{pointId} - ошибка: чужая точка")
    void testDeletePoint_WrongUser() throws Exception {
        // Given
        Point existing = createPoint("sport", "Спортзал", "Адрес");
        UUID anotherUserId = UUID.randomUUID();

        // When & Then - для чужого userId точка не найдена → 404
        mockMvc.perform(delete("/api/users/{userId}/points/{pointId}",
                anotherUserId, existing.getId())
                .header("Authorization", authHeader()))
                .andExpect(status().isNotFound());

        // Точка в БД не тронута
        assertTrue(pointRepository.findById(existing.getId()).isPresent());
    }

    @Test
    @DisplayName("DELETE /api/users/{userId}/points/{pointId} - ошибка: не авторизован")
    void testDeletePoint_Unauthorized() throws Exception {
        Point existing = createPoint("leisure", "Дом", "Адрес");

        mockMvc.perform(delete("/api/users/{userId}/points/{pointId}",
                testUser.getId(), existing.getId()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    @DisplayName("DELETE /api/users/{userId}/points/{pointId} - удаление не влияет на другие точки")
    void testDeletePoint_DoesNotDeleteOtherPoints() throws Exception {
        // Given
        Point toDelete = createPoint("work", "Работа", "Адрес 1");
        Point toKeep = createPoint("sport", "Спортзал", "Адрес 2");

        // When
        mockMvc.perform(delete("/api/users/{userId}/points/{pointId}",
                testUser.getId(), toDelete.getId())
                .header("Authorization", authHeader()))
                .andExpect(status().isNoContent());

        // Then
        assertFalse(pointRepository.findById(toDelete.getId()).isPresent());
        assertTrue(pointRepository.findById(toKeep.getId()).isPresent());
    }
}
