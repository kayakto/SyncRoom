package ru.syncroom.projector.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.security.JwtTokenService;
import ru.syncroom.projector.domain.ProjectorSession;
import ru.syncroom.projector.repository.ProjectorSessionRepository;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты для ProjectorController.
 * Покрывает REST-эндпоинты:
 *  - GET  /api/rooms/{roomId}/projector
 *  - POST /api/rooms/{roomId}/projector
 *  - DELETE /api/rooms/{roomId}/projector
 *  - POST /api/projector/srs-callback
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Projector Controller Integration Tests")
class ProjectorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomParticipantRepository participantRepository;

    @Autowired
    private ProjectorSessionRepository projectorSessionRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private User hostUser;
    private String hostAccessToken;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        projectorSessionRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        hostUser = userRepository.save(User.builder()
                .name("Projector Host")
                .email("projector-host@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());

        hostAccessToken = jwtTokenService.generateAccessToken(
                hostUser.getId(),
                hostUser.getName(),
                hostUser.getEmail(),
                hostUser.getProvider().getValue());
    }

    private String authHeader(String token) {
        return "Bearer " + token;
    }

    private String authHeaderForHost() {
        return authHeader(hostAccessToken);
    }

    private Room createRoom(String context) {
        return roomRepository.save(Room.builder()
                .context(context)
                .title("Test room")
                .maxParticipants(10)
                .isActive(true)
                .build());
    }

    private void addParticipant(Room room, User user) {
        participantRepository.save(RoomParticipant.builder()
                .room(room)
                .user(user)
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // POST /api/rooms/{roomId}/projector
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/rooms/{roomId}/projector (EMBED) - успешный запуск проектора")
    void startProjector_Embed_Success() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, hostUser);

        String body = """
                {
                  "mode": "EMBED",
                  "videoUrl": "https://vk.com/video_ext.php?oid=-12345&id=67890",
                  "videoTitle": "Лекция по матану"
                }
                """;

        mockMvc.perform(post("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeaderForHost())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.mode").value("EMBED"))
                .andExpect(jsonPath("$.videoUrl").value(containsString("vk.com")))
                .andExpect(jsonPath("$.videoTitle").value("Лекция по матану"))
                .andExpect(jsonPath("$.isPlaying").value(false))
                .andExpect(jsonPath("$.positionMs").value(0));

        // В БД должна появиться одна projector_sessions запись
        assertThat(projectorSessionRepository.findByRoomId(room.getId())).isPresent();
    }

    @Test
    @DisplayName("POST /api/rooms/{roomId}/projector (STREAM) - успешный запуск RTMP-стрима")
    void startProjector_Stream_Success() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, hostUser);

        String body = """
                {
                  "mode": "STREAM",
                  "videoTitle": "Стрим Полины"
                }
                """;

        mockMvc.perform(post("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeaderForHost())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.mode").value("STREAM"))
                .andExpect(jsonPath("$.videoTitle").value("Стрим Полины"))
                .andExpect(jsonPath("$.streamKey").value("room-" + room.getId()))
                .andExpect(jsonPath("$.videoUrl").value("http://localhost:8085/live/" + "room-" + room.getId() + ".m3u8"))
                .andExpect(jsonPath("$.isLive").value(false))
                .andExpect(jsonPath("$.rtmpUrl").value("rtmp://localhost:1935/live/" + "room-" + room.getId()));
    }

    @Test
    @DisplayName("POST /api/rooms/{roomId}/projector - ошибка, если не участник комнаты → 403")
    void startProjector_ForbiddenForNonParticipant() throws Exception {
        Room room = createRoom("study");
        // hostUser не добавлен в участники

        String body = """
                {
                  "mode": "EMBED",
                  "videoUrl": "https://example.com/video.mp4"
                }
                """;

        mockMvc.perform(post("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeaderForHost())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // GET /api/rooms/{roomId}/projector
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/rooms/{roomId}/projector - 404, если проектор не включён")
    void getProjector_NotFoundWhenNoSession() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, hostUser);

        mockMvc.perform(get("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeaderForHost()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/rooms/{roomId}/projector - rtmpUrl возвращается только хосту")
    void getProjector_RtmpUrlVisibleOnlyForHost() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, hostUser);

        // сначала запускаем STREAM-проектор от имени хоста
        String body = """
                {
                  "mode": "STREAM",
                  "videoTitle": "Стрим Полины"
                }
                """;

        mockMvc.perform(post("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeaderForHost())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Хост видит rtmpUrl
        mockMvc.perform(get("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeaderForHost()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rtmpUrl").isString());

        // Другой пользователь (участник комнаты) rtmpUrl не видит
        User viewer = userRepository.save(User.builder()
                .name("Viewer")
                .email("viewer@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
        addParticipant(room, viewer);

        String viewerToken = jwtTokenService.generateAccessToken(
                viewer.getId(),
                viewer.getName(),
                viewer.getEmail(),
                viewer.getProvider().getValue());

        mockMvc.perform(get("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeader(viewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rtmpUrl").doesNotExist());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // DELETE /api/rooms/{roomId}/projector
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/rooms/{roomId}/projector - успешное выключение хостом")
    void stopProjector_SuccessByHost() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, hostUser);

        String body = """
                {
                  "mode": "EMBED",
                  "videoUrl": "https://example.com/video.mp4"
                }
                """;

        mockMvc.perform(post("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeaderForHost())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(projectorSessionRepository.findByRoomId(room.getId())).isPresent();

        mockMvc.perform(delete("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeaderForHost()))
                .andExpect(status().isNoContent());

        assertThat(projectorSessionRepository.findByRoomId(room.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/rooms/{roomId}/projector - 403, если не хост")
    void stopProjector_ForbiddenForNonHost() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, hostUser);

        String body = """
                {
                  "mode": "EMBED",
                  "videoUrl": "https://example.com/video.mp4"
                }
                """;

        mockMvc.perform(post("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeaderForHost())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        User anotherUser = userRepository.save(User.builder()
                .name("Another")
                .email("another@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
        addParticipant(room, anotherUser);

        String anotherToken = jwtTokenService.generateAccessToken(
                anotherUser.getId(),
                anotherUser.getName(),
                anotherUser.getEmail(),
                anotherUser.getProvider().getValue());

        mockMvc.perform(delete("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeader(anotherToken)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // POST /api/projector/srs-callback
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/projector/srs-callback - on_publish и on_unpublish обновляют isLive")
    void srsCallback_UpdatesIsLiveFlag() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, hostUser);

        // создаём STREAM-сессию через сервисный эндпоинт
        String body = """
                {
                  "mode": "STREAM",
                  "videoTitle": "Test stream"
                }
                """;

        mockMvc.perform(post("/api/rooms/{roomId}/projector", room.getId())
                        .header("Authorization", authHeaderForHost())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        ProjectorSession session = projectorSessionRepository.findByRoomId(room.getId())
                .orElseThrow();

        // on_publish → isLive = true
        String publishBody = """
                {
                  "action": "on_publish",
                  "stream": "%s",
                  "ip": "127.0.0.1"
                }
                """.formatted(session.getStreamKey());

        mockMvc.perform(post("/api/projector/srs-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", is(0)));

        ProjectorSession afterPublish = projectorSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(afterPublish.getIsLive()).isTrue();

        // on_unpublish → isLive = false
        String unpublishBody = """
                {
                  "action": "on_unpublish",
                  "stream": "%s",
                  "ip": "127.0.0.1"
                }
                """.formatted(session.getStreamKey());

        mockMvc.perform(post("/api/projector/srs-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unpublishBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(0)));

        ProjectorSession afterUnpublish = projectorSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(afterUnpublish.getIsLive()).isFalse();
    }
}

