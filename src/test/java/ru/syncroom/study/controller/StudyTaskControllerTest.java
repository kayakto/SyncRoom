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
import ru.syncroom.study.domain.StudyTask;
import ru.syncroom.study.repository.StudyTaskRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("StudyTask Controller Integration Tests")
class StudyTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomParticipantRepository participantRepository;

    @Autowired
    private StudyTaskRepository taskRepository;

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
        taskRepository.deleteAll();
        participantRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .name("Task User")
                .email("task@example.com")
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

    private Room createRoom() {
        return roomRepository.save(Room.builder()
                .context("study")
                .title("Study room")
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
    @DisplayName("GET /tasks - возвращает только таски текущего пользователя")
    void getMyTasks() throws Exception {
        Room room = createRoom();
        addParticipant(room, user);

        // чужой пользователь и его таск
        User other = userRepository.save(User.builder()
                .name("Other")
                .email("other@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
        addParticipant(room, other);

        taskRepository.save(StudyTask.builder()
                .user(user)
                .room(room)
                .text("My task")
                .isDone(false)
                .sortOrder(0)
                .build());

        taskRepository.save(StudyTask.builder()
                .user(other)
                .room(room)
                .text("Other task")
                .isDone(false)
                .sortOrder(0)
                .build());

        mockMvc.perform(get("/api/rooms/{roomId}/tasks", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].text").value("My task"));
    }

    @Test
    @DisplayName("POST /tasks - создаёт таск с увеличивающимся sortOrder")
    void createTask_IncrementsSortOrder() throws Exception {
        Room room = createRoom();
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/tasks", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(0));

        mockMvc.perform(post("/api/rooms/{roomId}/tasks", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"B\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(1));
    }

    @Test
    @DisplayName("PUT /tasks/{taskId} - обновляет только переданные поля")
    void updateTask() throws Exception {
        Room room = createRoom();
        addParticipant(room, user);

        StudyTask task = taskRepository.save(StudyTask.builder()
                .user(user)
                .room(room)
                .text("Old")
                .isDone(false)
                .sortOrder(5)
                .build());

        mockMvc.perform(put("/api/rooms/{roomId}/tasks/{taskId}", room.getId(), task.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"New\", \"isDone\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("New"))
                .andExpect(jsonPath("$.isDone").value(true))
                .andExpect(jsonPath("$.sortOrder").value(5));
    }

    @Test
    @DisplayName("DELETE /tasks/{taskId} - удаляет таск")
    void deleteTask() throws Exception {
        Room room = createRoom();
        addParticipant(room, user);

        StudyTask task = taskRepository.save(StudyTask.builder()
                .user(user)
                .room(room)
                .text("Delete me")
                .isDone(false)
                .sortOrder(0)
                .build());

        mockMvc.perform(delete("/api/rooms/{roomId}/tasks/{taskId}", room.getId(), task.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isNoContent());
    }
}

