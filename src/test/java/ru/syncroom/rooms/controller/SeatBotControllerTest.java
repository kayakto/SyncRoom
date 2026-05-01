package ru.syncroom.rooms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import ru.syncroom.rooms.config.SeatBotProperties;
import ru.syncroom.rooms.domain.ParticipantRole;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.domain.RoomSeatBot;
import ru.syncroom.rooms.domain.Seat;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.rooms.repository.RoomSeatBotRepository;
import ru.syncroom.rooms.repository.SeatRepository;
import ru.syncroom.rooms.ws.RoomEvent;
import ru.syncroom.rooms.ws.RoomEventType;
import ru.syncroom.rooms.ws.SeatTakenPayload;
import ru.syncroom.study.domain.StudyTask;
import ru.syncroom.study.repository.StudyTaskRepository;
import ru.syncroom.study.repository.TaskLikeRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Seat bots — REST и интеграция с комнатой")
class SeatBotControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomParticipantRepository participantRepository;
    @Autowired
    private RoomSeatBotRepository roomSeatBotRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private StudyTaskRepository studyTaskRepository;
    @Autowired
    private TaskLikeRepository taskLikeRepository;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private SeatBotProperties seatBotProperties;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private User member;
    private User outsider;
    private String memberToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        purge();

        member = userRepository.save(User.builder()
                .name("SeatBot Member")
                .email("seatbot-member@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());
        outsider = userRepository.save(User.builder()
                .name("Outsider")
                .email("outsider@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());
        memberToken = bearer(member);
        outsiderToken = bearer(outsider);
    }

    private void purge() {
        taskLikeRepository.deleteAll();
        studyTaskRepository.deleteAll();
        participantRepository.deleteAll();
        roomSeatBotRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String bearer(User u) {
        return "Bearer " + jwtTokenService.generateAccessToken(
                u.getId(), u.getName(), u.getEmail(), u.getProvider().getValue());
    }

    private Room workRoom() {
        return roomRepository.save(Room.builder()
                .context("work")
                .title("Работа")
                .maxParticipants(10)
                .isActive(true)
                .build());
    }

    private Room studyRoom() {
        return roomRepository.save(Room.builder()
                .context("study")
                .title("Учёба")
                .maxParticipants(10)
                .isActive(true)
                .build());
    }

    private Room leisureRoom() {
        return roomRepository.save(Room.builder()
                .context("leisure")
                .title("Дом")
                .maxParticipants(10)
                .isActive(true)
                .build());
    }

    private Seat freeSeat(Room room, double x, double y) {
        return seatRepository.save(Seat.builder().room(room).x(x).y(y).occupiedBy(null).build());
    }

    private void join(Room room, User user) {
        participantRepository.save(RoomParticipant.builder()
                .room(room)
                .user(user)
                .role(ParticipantRole.OBSERVER)
                .build());
    }

    private String jsonBotType(String botType) throws Exception {
        return objectMapper.writeValueAsString(Map.of("botType", botType));
    }

    @Test
    @DisplayName("GET /api/rooms/seat-bots/catalog?context=WORK — только WORK_FOCUS_BUDDY")
    void catalog_work() throws Exception {
        mockMvc.perform(get("/api/rooms/seat-bots/catalog").param("context", "WORK")
                        .header("Authorization", memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].botType").value("WORK_FOCUS_BUDDY"))
                .andExpect(jsonPath("$[0].behaviour.reactsToPomodoro").value(true))
                .andExpect(jsonPath("$[0].avatarUrl").value("/icons/icon-192.png"));
    }

    @Test
    @DisplayName("GET /api/rooms/seat-bots/catalog?context=STUDY — два типа")
    void catalog_study_twoKinds() throws Exception {
        mockMvc.perform(get("/api/rooms/seat-bots/catalog").param("context", "STUDY")
                        .header("Authorization", memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("POST .../seats/{id}/bot — посадка WORK_FOCUS_BUDDY, occupiedBy.isBot=true")
    void placeBot_work_ok() throws Exception {
        Room room = workRoom();
        join(room, member);
        Seat seat = freeSeat(room, 0.1, 0.2);

        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), seat.getId())
                        .header("Authorization", memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBotType("WORK_FOCUS_BUDDY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedBy.id").exists())
                .andExpect(jsonPath("$.occupiedBy.name").value("Lofi Cat"))
                .andExpect(jsonPath("$.occupiedBy.isBot").value(true));

        assertEquals(1, roomSeatBotRepository.countByRoom_Id(room.getId()));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/topic/room/" + room.getId() + "/seats"),
                argThat((RoomEvent ev) -> ev.getType() == RoomEventType.SEAT_TAKEN
                        && ev.getPayload() instanceof SeatTakenPayload p
                        && Boolean.TRUE.equals(p.getUser().getIsBot())));
    }

    @Test
    @DisplayName("POST .../bot — leisure → 400")
    void placeBot_leisure_forbidden() throws Exception {
        Room room = leisureRoom();
        join(room, member);
        Seat seat = freeSeat(room, 0.1, 0.2);
        String body = objectMapper.writeValueAsString(Map.of("botType", "WORK_FOCUS_BUDDY"));
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), seat.getId())
                        .header("Authorization", memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("leisure")));
    }

    @Test
    @DisplayName("POST .../bot — дубликат botType в комнате → 400")
    void placeBot_duplicateType() throws Exception {
        Room room = studyRoom();
        join(room, member);
        Seat s1 = freeSeat(room, 0.1, 0.2);
        Seat s2 = freeSeat(room, 0.2, 0.2);
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), s1.getId())
                        .header("Authorization", memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBotType("WORK_FOCUS_BUDDY")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), s2.getId())
                        .header("Authorization", memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBotType("WORK_FOCUS_BUDDY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("already")));
    }

    @Test
    @DisplayName("POST .../bot — лимит ботов на комнату")
    void placeBot_maxPerRoom() throws Exception {
        int savedMax = seatBotProperties.getMaxPerRoom();
        try {
            seatBotProperties.setMaxPerRoom(1);
            Room room = studyRoom();
            join(room, member);
            Seat s1 = freeSeat(room, 0.1, 0.2);
            Seat s2 = freeSeat(room, 0.2, 0.2);
            mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), s1.getId())
                            .header("Authorization", memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonBotType("WORK_FOCUS_BUDDY")))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), s2.getId())
                            .header("Authorization", memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonBotType("STUDY_HELPER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Maximum")));
        } finally {
            seatBotProperties.setMaxPerRoom(savedMax);
        }
    }

    @Test
    @DisplayName("POST .../bot — не участник → 403")
    void placeBot_notParticipant_forbidden() throws Exception {
        Room room = workRoom();
        join(room, member);
        Seat seat = freeSeat(room, 0.1, 0.2);
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), seat.getId())
                        .header("Authorization", outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBotType("WORK_FOCUS_BUDDY")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE .../seats/{id}/bot — снятие бота")
    void removeBot_ok() throws Exception {
        Room room = workRoom();
        join(room, member);
        Seat seat = freeSeat(room, 0.1, 0.2);
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), seat.getId())
                        .header("Authorization", memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBotType("WORK_FOCUS_BUDDY")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), seat.getId())
                        .header("Authorization", memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedBy").value(nullValue()));

        assertEquals(0, roomSeatBotRepository.countByRoom_Id(room.getId()));
    }

    @Test
    @DisplayName("DELETE .../bot — на месте человек → 400")
    void removeBot_humanOccupant_badRequest() throws Exception {
        Room room = workRoom();
        join(room, member);
        Seat seat = freeSeat(room, 0.1, 0.2);
        seat.setOccupiedBy(member);
        seatRepository.save(seat);

        mockMvc.perform(delete("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), seat.getId())
                        .header("Authorization", memberToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("human")));
    }

    @Test
    @DisplayName("GET /api/rooms/{id}/seat-bots — список ботов в комнате")
    void listBotsInRoom() throws Exception {
        Room room = workRoom();
        join(room, member);
        Seat seat = freeSeat(room, 0.1, 0.2);
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), seat.getId())
                        .header("Authorization", memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBotType("WORK_FOCUS_BUDDY")));

        mockMvc.perform(get("/api/rooms/{roomId}/seat-bots", room.getId())
                        .header("Authorization", memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].botType").value("WORK_FOCUS_BUDDY"))
                .andExpect(jsonPath("$[0].seatId").value(seat.getId().toString()));
    }

    @Test
    @DisplayName("Последний человек выходит — seat-боты удаляются")
    void leaveRoom_clearsSeatBots() throws Exception {
        Room room = workRoom();
        join(room, member);
        Seat seat = freeSeat(room, 0.1, 0.2);
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/bot", room.getId(), seat.getId())
                        .header("Authorization", memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBotType("WORK_FOCUS_BUDDY")))
                .andExpect(status().isOk());
        assertEquals(1, roomSeatBotRepository.countByRoom_Id(room.getId()));

        mockMvc.perform(post("/api/rooms/{roomId}/leave", room.getId())
                        .header("Authorization", memberToken))
                .andExpect(status().isNoContent());

        assertEquals(0, roomSeatBotRepository.countByRoom_Id(room.getId()));
    }

    @Test
    @DisplayName("Лидерборд учитывает задачи и лайки seat-бота")
    void leaderboard_includesSeatBot() throws Exception {
        Room room = studyRoom();
        join(room, member);
        Seat seat = freeSeat(room, 0.5, 0.5);
        RoomSeatBot bot = roomSeatBotRepository.save(RoomSeatBot.builder()
                .room(room)
                .botType("STUDY_HELPER")
                .seat(seat)
                .name("Учебник Андрюша")
                .avatarUrl("/icons/icon-192.png")
                .build());
        participantRepository.save(RoomParticipant.builder()
                .room(room)
                .seatBot(bot)
                .role(ParticipantRole.PARTICIPANT)
                .build());
        StudyTask botTask = studyTaskRepository.save(StudyTask.builder()
                .user(null)
                .ownerSeatBot(bot)
                .room(room)
                .text("Bot goal")
                .isDone(true)
                .sortOrder(0)
                .build());
        StudyTask humanTask = studyTaskRepository.save(StudyTask.builder()
                .user(member)
                .room(room)
                .text("Human goal")
                .isDone(false)
                .sortOrder(1)
                .build());
        taskLikeRepository.save(ru.syncroom.study.domain.TaskLike.builder()
                .task(botTask)
                .user(member)
                .build());

        mockMvc.perform(get("/api/rooms/{roomId}/leaderboard", room.getId())
                        .header("Authorization", memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(bot.getId().toString()))
                .andExpect(jsonPath("$[0].totalLikes").value(1))
                .andExpect(jsonPath("$[1].userId").value(member.getId().toString()))
                .andExpect(jsonPath("$[1].totalLikes").value(0));
    }
}
