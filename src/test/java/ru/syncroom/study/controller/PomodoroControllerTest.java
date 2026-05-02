package ru.syncroom.study.controller;

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
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.study.dto.PomodoroStartRequest;
import ru.syncroom.study.repository.PomodoroSessionRepository;
import ru.syncroom.study.service.PomodoroService;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Pomodoro Controller Integration Tests")
class PomodoroControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomParticipantRepository participantRepository;

    @Autowired
    private PomodoroSessionRepository pomodoroSessionRepository;

    @Autowired
    private PomodoroService pomodoroService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        pomodoroSessionRepository.deleteAll();
        participantRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .name("Pomodoro User")
                .email("pomodoro@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());

        token = jwtTokenService.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(), user.getProvider().getValue());
    }

    private String auth() {
        return "Bearer " + token;
    }

    private User pomodoroBotUser() {
        return userRepository.findByEmail("pomodorobot@syncroom.local")
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("PomodoroBot")
                        .email("pomodorobot@syncroom.local")
                        .provider(AuthProvider.EMAIL)
                        .passwordHash("hashed")
                        .createdAt(OffsetDateTime.now())
                        .build()));
    }

    private Room createRoom(String context) {
        return roomRepository.save(Room.builder()
                .context(context)
                .title("Room " + context)
                .maxParticipants(10)
                .isActive(true)
                .build());
    }

    private void addParticipant(Room room, User u) {
        participantRepository.save(RoomParticipant.builder()
                .room(room)
                .user(u)
                .build());
    }

    private void startPomodoroViaServer(Room room) {
        PomodoroStartRequest request = new PomodoroStartRequest();
        request.setWorkDuration(10);
        request.setBreakDuration(5);
        request.setLongBreakDuration(7);
        request.setRoundsTotal(2);
        pomodoroService.startByBot(room.getId(), pomodoroBotUser().getId(), request);
    }

    @Test
    @DisplayName("GET /pomodoro — участник видит состояние после серверного старта")
    void getPomodoro_afterServerStart_ok() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);
        startPomodoroViaServer(room);

        mockMvc.perform(get("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.phase").value("WORK"))
                .andExpect(jsonPath("$.serverControlled").value(true))
                .andExpect(jsonPath("$.workDuration").value(10));
    }

    @Test
    @DisplayName("GET /pomodoro — 400 если пользователь не в комнате")
    void getPomodoro_nonParticipant_badRequest() throws Exception {
        Room room = createRoom("study");
        startPomodoroViaServer(room);

        mockMvc.perform(get("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("participant")));
    }

    @Test
    @DisplayName("work-комната: помодоро недоступен (только study)")
    void getPomodoro_workContext_notAvailable() throws Exception {
        Room room = createRoom("work");
        addParticipant(room, user);

        mockMvc.perform(get("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not available")));
    }

    @Test
    @DisplayName("POST /pomodoro/start — эндпоинт удалён (старт только на сервере)")
    void postStart_removed_notMapped() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/start", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /pomodoro/start - 400 для leisure/sport (помодоро недоступен) — через сервис")
    void startByBot_DisallowedContext() {
        Room room = createRoom("leisure");
        addParticipant(room, user);

        org.junit.jupiter.api.Assertions.assertThrows(
                ru.syncroom.common.exception.BadRequestException.class,
                () -> pomodoroService.startByBot(room.getId(), pomodoroBotUser().getId(), new PomodoroStartRequest())
        );
    }

    @Test
    @DisplayName("GET /pomodoro - 404 если не запущен")
    void getPomodoro_NotFound() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);

        mockMvc.perform(get("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /pomodoro - 400 для комнаты без поддержки помодоро")
    void getPomodoro_DisallowedContext() throws Exception {
        Room room = createRoom("sport");
        addParticipant(room, user);

        mockMvc.perform(get("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not available")));
    }

    @Test
    @DisplayName("DELETE /pomodoro по HTTP не поддерживается (клиент только GET)")
    void deletePomodoro_httpMethodNotAllowed() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);
        startPomodoroViaServer(room);

        mockMvc.perform(delete("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("PomodoroService.stop снимает сессию (не REST)")
    void stopViaService_clearsSession() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);
        startPomodoroViaServer(room);

        pomodoroService.stop(room.getId(), user.getId());

        mockMvc.perform(get("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("startByBot в work-комнате — BadRequest")
    void startByBot_workContext_disallowed() {
        Room room = createRoom("work");
        addParticipant(room, user);

        org.junit.jupiter.api.Assertions.assertThrows(
                ru.syncroom.common.exception.BadRequestException.class,
                () -> pomodoroService.startByBot(room.getId(), pomodoroBotUser().getId(), new PomodoroStartRequest())
        );
    }

    @Test
    @DisplayName("ручной pause/skip больше не доступны по HTTP")
    void manualPauseAndSkip_removed() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);
        startPomodoroViaServer(room);

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/pause", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/skip", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("startByBot с пустым запросом — длительности по умолчанию (25/5/15 мин, 4 раунда)")
    void startByBot_emptyRequest_usesDefaultDurations() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);
        pomodoroService.startByBot(room.getId(), pomodoroBotUser().getId(), new PomodoroStartRequest());

        mockMvc.perform(get("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDuration").value(PomodoroService.DEFAULT_WORK_DURATION_SECONDS))
                .andExpect(jsonPath("$.breakDuration").value(PomodoroService.DEFAULT_BREAK_DURATION_SECONDS))
                .andExpect(jsonPath("$.longBreakDuration").value(PomodoroService.DEFAULT_LONG_BREAK_DURATION_SECONDS))
                .andExpect(jsonPath("$.roundsTotal").value(PomodoroService.DEFAULT_ROUNDS_TOTAL))
                .andExpect(jsonPath("$.currentRound").value(1))
                .andExpect(jsonPath("$.phase").value("WORK"));
    }

    @Test
    @DisplayName("POST на /pomodoro (без /start) — 405, доступен только GET")
    void postToPomodoroResource_methodNotAllowed() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value(containsString("Method not allowed")));
    }

    @Test
    @DisplayName("PUT на /pomodoro — 405")
    void putPomodoro_methodNotAllowed() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);

        mockMvc.perform(put("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("work: DELETE /pomodoro — 405 (не 400 контекста: метод не объявлен)")
    void deletePomodoro_workRoom_methodNotAllowed() throws Exception {
        Room room = createRoom("work");
        addParticipant(room, user);

        mockMvc.perform(delete("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("leisure: GET /pomodoro — 400 (контекст не study)")
    void getPomodoro_leisure_notAvailable() throws Exception {
        Room room = createRoom("leisure");
        addParticipant(room, user);

        mockMvc.perform(get("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not available")));
    }

    @Test
    @DisplayName("PomodoroService.stop — не-участник не может сбросить сессию")
    void stop_nonParticipant_throws() {
        Room room = createRoom("study");
        startPomodoroViaServer(room);

        org.junit.jupiter.api.Assertions.assertThrows(
                ru.syncroom.common.exception.BadRequestException.class,
                () -> pomodoroService.stop(room.getId(), user.getId()),
                "User is not a participant"
        );
    }
}
