package ru.syncroom.study.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import ru.syncroom.rooms.repository.RoomSeatBotRepository;
import ru.syncroom.study.domain.StudyTask;
import ru.syncroom.study.repository.StudyTaskRepository;
import ru.syncroom.study.repository.TaskLikeRepository;
import ru.syncroom.study.ws.StudyTaskWsEvent;
import ru.syncroom.study.ws.StudyTaskWsEventType;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private RoomSeatBotRepository roomSeatBotRepository;

    @Autowired
    private StudyTaskRepository taskRepository;

    @Autowired
    private TaskLikeRepository taskLikeRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        taskLikeRepository.deleteAll();
        taskRepository.deleteAll();
        participantRepository.deleteAll();
        roomSeatBotRepository.deleteAll();
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

    private Room createRoom(String context) {
        return roomRepository.save(Room.builder()
                .context(context)
                .title(context + " room")
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

        verify(messagingTemplate, times(2)).convertAndSend(
                eq("/topic/room/" + room.getId() + "/tasks"),
                argThat((StudyTaskWsEvent ev) ->
                        ev.getType() == StudyTaskWsEventType.TASK_CREATED
                                && ((Map<?, ?>) ev.getPayload()).containsKey("ownerId")
                                && ((Map<?, ?>) ev.getPayload()).containsKey("ownerName")
                                && ((Map<?, ?>) ev.getPayload()).containsKey("likedByMe")));
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

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + room.getId() + "/tasks"),
                argThat((StudyTaskWsEvent ev) ->
                        ev.getType() == StudyTaskWsEventType.TASK_UPDATED
                                && ((Map<?, ?>) ev.getPayload()).get("taskId").equals(task.getId().toString())
                                && ((Map<?, ?>) ev.getPayload()).get("ownerId").equals(user.getId().toString())
                                && ((Map<?, ?>) ev.getPayload()).get("ownerName").equals(user.getName())));
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

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + room.getId() + "/tasks"),
                argThat((StudyTaskWsEvent ev) ->
                        ev.getType() == StudyTaskWsEventType.TASK_DELETED
                                && ((Map<?, ?>) ev.getPayload()).get("taskId").equals(task.getId().toString())
                                && ((Map<?, ?>) ev.getPayload()).get("ownerId").equals(user.getId().toString())));
    }

    @Test
    @DisplayName("POST like — нельзя лайкать свою цель")
    void likeTask_cannotLikeOwn() throws Exception {
        Room room = createRoom();
        addParticipant(room, user);
        StudyTask task = taskRepository.save(StudyTask.builder()
                .user(user)
                .room(room)
                .text("Mine")
                .isDone(false)
                .sortOrder(0)
                .build());

        mockMvc.perform(post("/api/rooms/{roomId}/tasks/{taskId}/like", room.getId(), task.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST like / DELETE like — счётчик и WS TASK_LIKED / TASK_UNLIKED")
    void likeUnlikeTask() throws Exception {
        Room room = createRoom();
        addParticipant(room, user);
        User other = userRepository.save(User.builder()
                .name("Other")
                .email("other2@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
        addParticipant(room, other);
        StudyTask task = taskRepository.save(StudyTask.builder()
                .user(other)
                .room(room)
                .text("Their task")
                .isDone(false)
                .sortOrder(0)
                .build());

        mockMvc.perform(post("/api/rooms/{roomId}/tasks/{taskId}/like", room.getId(), task.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(true));

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + room.getId() + "/tasks"),
                argThat((StudyTaskWsEvent ev) ->
                        ev.getType() == StudyTaskWsEventType.TASK_LIKED
                                && ((Map<?, ?>) ev.getPayload()).get("action").equals("LIKE")));

        mockMvc.perform(delete("/api/rooms/{roomId}/tasks/{taskId}/like", room.getId(), task.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByMe").value(false));

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + room.getId() + "/tasks"),
                argThat((StudyTaskWsEvent ev) ->
                        ev.getType() == StudyTaskWsEventType.TASK_UNLIKED));
    }

    @Test
    @DisplayName("GET /tasks/all — все цели с likeCount и likedByMe")
    void getAllTasksWithLikes() throws Exception {
        Room room = createRoom();
        addParticipant(room, user);
        User other = userRepository.save(User.builder()
                .name("Peer")
                .email("peer@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
        addParticipant(room, other);
        StudyTask mine = taskRepository.save(StudyTask.builder()
                .user(user)
                .room(room)
                .text("A")
                .isDone(false)
                .sortOrder(0)
                .build());
        StudyTask theirs = taskRepository.save(StudyTask.builder()
                .user(other)
                .room(room)
                .text("B")
                .isDone(true)
                .sortOrder(0)
                .build());
        mockMvc.perform(post("/api/rooms/{roomId}/tasks/{taskId}/like", room.getId(), theirs.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk());

        var mvcResult = mockMvc.perform(get("/api/rooms/{roomId}/tasks/all", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();
        JsonNode arr = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        int likedByMe = 0;
        for (JsonNode n : arr) {
            if (n.path("text").asText().equals("B")) {
                assertEquals(1, n.path("likeCount").asLong());
                assertEquals(true, n.path("likedByMe").asBoolean());
            }
            if (n.path("text").asText().equals("A")) {
                assertEquals(0, n.path("likeCount").asLong());
                assertEquals(false, n.path("likedByMe").asBoolean());
            }
            if (n.path("likedByMe").asBoolean()) {
                likedByMe++;
            }
        }
        assertEquals(1, likedByMe);
    }

    @Test
    @DisplayName("GET /leaderboard — сортировка по totalLikes")
    void leaderboard() throws Exception {
        Room room = createRoom();
        User rich = userRepository.save(User.builder()
                .name("Rich")
                .email("rich@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
        User poor = userRepository.save(User.builder()
                .name("Poor")
                .email("poor@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
        addParticipant(room, user);
        addParticipant(room, rich);
        addParticipant(room, poor);
        StudyTask tRich = taskRepository.save(StudyTask.builder()
                .user(rich)
                .room(room)
                .text("R")
                .isDone(false)
                .sortOrder(0)
                .build());
        StudyTask tPoor = taskRepository.save(StudyTask.builder()
                .user(poor)
                .room(room)
                .text("P")
                .isDone(false)
                .sortOrder(0)
                .build());
        mockMvc.perform(post("/api/rooms/{roomId}/tasks/{taskId}/like", room.getId(), tRich.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/{roomId}/tasks/{taskId}/like", room.getId(), tRich.getId())
                        .header("Authorization", "Bearer " + jwtTokenService.generateAccessToken(
                                poor.getId(), poor.getName(), poor.getEmail(), poor.getProvider().getValue())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/{roomId}/leaderboard", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(rich.getId().toString()))
                .andExpect(jsonPath("$[0].totalLikes").value(2))
                .andExpect(jsonPath("$[1].totalLikes").value(0));
    }

    @Test
    @DisplayName("GET /leaderboard — 400 в leisure-комнате")
    void leaderboard_NotAvailableForLeisureRoom() throws Exception {
        Room room = createRoom("leisure");
        addParticipant(room, user);

        mockMvc.perform(get("/api/rooms/{roomId}/leaderboard", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not available")));
    }

    @Test
    @DisplayName("GET /leaderboard/me — возвращает только текущего пользователя")
    void leaderboardMe_returnsCurrentUserOnly() throws Exception {
        Room room = createRoom();
        addParticipant(room, user);

        User other = userRepository.save(User.builder()
                .name("OtherLB")
                .email("otherlb@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
        addParticipant(room, other);

        StudyTask myDone = taskRepository.save(StudyTask.builder()
                .user(user)
                .room(room)
                .text("Mine done")
                .isDone(true)
                .sortOrder(0)
                .build());
        taskRepository.save(StudyTask.builder()
                .user(user)
                .room(room)
                .text("Mine todo")
                .isDone(false)
                .sortOrder(1)
                .build());
        taskRepository.save(StudyTask.builder()
                .user(other)
                .room(room)
                .text("Other task")
                .isDone(false)
                .sortOrder(0)
                .build());

        String otherToken = "Bearer " + jwtTokenService.generateAccessToken(
                other.getId(), other.getName(), other.getEmail(), other.getProvider().getValue());

        // Другой пользователь лайкает мою цель, чтобы у меня было totalLikes=1.
        mockMvc.perform(post("/api/rooms/{roomId}/tasks/{taskId}/like", room.getId(), myDone.getId())
                        .header("Authorization", otherToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/{roomId}/leaderboard/me", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.userName").value(user.getName()))
                .andExpect(jsonPath("$.totalLikes").value(1))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.totalTasks").value(2));
    }

    @Test
    @DisplayName("POST like — не участник комнаты → 400")
    void likeTask_notParticipant() throws Exception {
        Room room = createRoom();
        addParticipant(room, user);
        User outsider = userRepository.save(User.builder()
                .name("Outsider")
                .email("out@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
        StudyTask task = taskRepository.save(StudyTask.builder()
                .user(user)
                .room(room)
                .text("T")
                .isDone(false)
                .sortOrder(0)
                .build());
        String outsiderToken = "Bearer " + jwtTokenService.generateAccessToken(
                outsider.getId(), outsider.getName(), outsider.getEmail(), outsider.getProvider().getValue());

        mockMvc.perform(post("/api/rooms/{roomId}/tasks/{taskId}/like", room.getId(), task.getId())
                        .header("Authorization", outsiderToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST like — цель из другой комнаты → 404")
    void likeTask_wrongRoom() throws Exception {
        Room roomA = createRoom();
        Room roomB = createRoom();
        addParticipant(roomA, user);
        addParticipant(roomB, user);
        StudyTask taskInA = taskRepository.save(StudyTask.builder()
                .user(user)
                .room(roomA)
                .text("Only A")
                .isDone(false)
                .sortOrder(0)
                .build());

        mockMvc.perform(post("/api/rooms/{roomId}/tasks/{taskId}/like", roomB.getId(), taskInA.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST like дважды — идемпотентно, WS TASK_LIKED только один раз")
    void likeTask_idempotentSecondPost_noExtraWs() throws Exception {
        Room room = createRoom();
        addParticipant(room, user);
        User other = userRepository.save(User.builder()
                .name("Owner")
                .email("owner@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
        addParticipant(room, other);
        StudyTask task = taskRepository.save(StudyTask.builder()
                .user(other)
                .room(room)
                .text("X")
                .isDone(false)
                .sortOrder(0)
                .build());

        mockMvc.perform(post("/api/rooms/{roomId}/tasks/{taskId}/like", room.getId(), task.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/{roomId}/tasks/{taskId}/like", room.getId(), task.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));

        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/room/" + room.getId() + "/tasks"),
                argThat((StudyTaskWsEvent ev) -> ev.getType() == StudyTaskWsEventType.TASK_LIKED));
    }

    @Test
    @DisplayName("DELETE like без лайка — 200, без WS на /tasks")
    void unlikeTask_withoutLike_noWs() throws Exception {
        Room room = createRoom();
        addParticipant(room, user);
        User other = userRepository.save(User.builder()
                .name("O")
                .email("o@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
        addParticipant(room, other);
        StudyTask task = taskRepository.save(StudyTask.builder()
                .user(other)
                .room(room)
                .text("Y")
                .isDone(false)
                .sortOrder(0)
                .build());

        mockMvc.perform(delete("/api/rooms/{roomId}/tasks/{taskId}/like", room.getId(), task.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByMe").value(false));

        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/room/" + room.getId() + "/tasks"),
                any(Object.class));
    }
}

