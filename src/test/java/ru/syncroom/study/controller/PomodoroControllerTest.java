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
import ru.syncroom.study.repository.PomodoroSessionRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    @DisplayName("POST /pomodoro/start - успех для study-комнаты")
    void startPomodoro_Success() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/start", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDuration\": 10, \"breakDuration\": 5, \"longBreakDuration\": 7, \"roundsTotal\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.phase").value("WORK"))
                .andExpect(jsonPath("$.workDuration").value(10))
                .andExpect(jsonPath("$.breakDuration").value(5))
                .andExpect(jsonPath("$.longBreakDuration").value(7))
                .andExpect(jsonPath("$.roundsTotal").value(2));
    }

    @Test
    @DisplayName("POST /pomodoro/start - 400 для work-комнаты")
    void startPomodoro_BadRequest_WorkRoom() throws Exception {
        Room room = createRoom("work");
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/start", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDuration\": 8, \"roundsTotal\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not available")));
    }

    @Test
    @DisplayName("POST /pomodoro/start - 400 для leisure/sport (помодоро недоступен)")
    void startPomodoro_DisallowedContext() throws Exception {
        Room room = createRoom("leisure");
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/start", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not available")));
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
    @DisplayName("GET /pomodoro - 200 после старта")
    void getPomodoro_AfterStart() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/start", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.phase").value("WORK"));
    }

    @Test
    @DisplayName("POST /pomodoro/pause и /resume - работают с remainingSeconds")
    void pauseAndResumePomodoro() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/start", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDuration\": 30}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/pause", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk());

        // После паузы фаза PAUSED
        mockMvc.perform(get("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PAUSED"));

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/resume", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("WORK"));
    }

    @Test
    @DisplayName("DELETE /pomodoro - стоп удаляет запись")
    void stopPomodoro() throws Exception {
        Room room = createRoom("study");
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/start", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("work-комната: pause / resume / skip / stop недоступны")
    void workRoom_PauseResumeSkipStop_Disallowed() throws Exception {
        Room room = createRoom("work");
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/pause", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not available")));

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/resume", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not available")));

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/skip", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not available")));

        mockMvc.perform(delete("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not available")));
    }
}

