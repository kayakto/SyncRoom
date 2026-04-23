package ru.syncroom.study.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import ru.syncroom.study.domain.RoomBot;
import ru.syncroom.study.domain.StudyTask;
import ru.syncroom.study.domain.BotGoalTemplate;
import ru.syncroom.study.repository.BotGoalTemplateRepository;
import ru.syncroom.study.repository.RoomBotRepository;
import ru.syncroom.study.repository.StudyTaskRepository;
import ru.syncroom.study.ws.StudyTaskWsEvent;
import ru.syncroom.study.ws.StudyTaskWsEventType;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("RoomBot Controller Integration Tests")
class RoomBotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomParticipantRepository participantRepository;

    @Autowired
    private RoomBotRepository roomBotRepository;

    @Autowired
    private BotGoalTemplateRepository botGoalTemplateRepository;

    @Autowired
    private StudyTaskRepository studyTaskRepository;

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
        studyTaskRepository.deleteAll();
        roomBotRepository.deleteAll();
        botGoalTemplateRepository.deleteAll();
        participantRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .name("Bot Test User")
                .email("room-bot-test@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());

        token = jwtTokenService.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(), user.getProvider().getValue());

        botGoalTemplateRepository.save(BotGoalTemplate.builder()
                .context("STUDY")
                .text("Сделать один фокус-спринт 25 минут")
                .category("productivity")
                .isActive(true)
                .build());
        botGoalTemplateRepository.save(BotGoalTemplate.builder()
                .context("STUDY")
                .text("Повторить конспект за прошлую тему")
                .category("learning")
                .isActive(true)
                .build());
        botGoalTemplateRepository.save(BotGoalTemplate.builder()
                .context("STUDY")
                .text("Решить 5 задач без подсказок")
                .category("learning")
                .isActive(true)
                .build());
    }

    private String auth() {
        return "Bearer " + token;
    }

    private Room createStudyRoom() {
        return roomRepository.save(Room.builder()
                .context("study")
                .title("Study room for bots")
                .maxParticipants(10)
                .isActive(true)
                .build());
    }

    private void addParticipant(Room room, User participant) {
        participantRepository.save(RoomParticipant.builder()
                .room(room)
                .user(participant)
                .build());
    }

    @Test
    @DisplayName("POST activate - активирует мотивационного бота и возвращает его состояние")
    void activateBot_success() throws Exception {
        Room room = createStudyRoom();
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/bots/motivational-goals/activate", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCount\": 1, \"autoSuggest\": false, \"suggestOnBreak\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botType").value("MOTIVATIONAL_GOALS"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.config").value(containsString("\"goalCount\":1")));

        RoomBot bot = roomBotRepository.findByRoom_Id(room.getId()).stream()
                .filter(b -> "MOTIVATIONAL_GOALS".equals(b.getBotType()))
                .findFirst()
                .orElseThrow();
        assertTrue(Boolean.TRUE.equals(bot.getIsActive()));
    }

    @Test
    @DisplayName("POST activate не создаёт цели до старта помодоро")
    void activateBot_doesNotGenerateTasksUntilPomodoroStarts() throws Exception {
        Room room = createStudyRoom();
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/bots/motivational-goals/activate", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCount\": 2, \"autoSuggest\": true, \"suggestOnBreak\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        List<StudyTask> tasksInRoom = studyTaskRepository.findByRoom_IdOrderByUser_IdAscSortOrderAsc(room.getId());
        assertEquals(0, tasksInRoom.size());
    }

    @Test
    @DisplayName("Старт помодоро и новый WORK-раунд создают новые цели бота")
    void pomodoroStartAndNewRound_generateBotGoals() throws Exception {
        Room room = createStudyRoom();
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/bots/motivational-goals/activate", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCount\": 1, \"autoSuggest\": true, \"suggestOnBreak\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/start", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDuration\": 120, \"breakDuration\": 60, \"roundsTotal\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("WORK"));

        List<StudyTask> afterStart = studyTaskRepository.findByRoom_IdOrderByUser_IdAscSortOrderAsc(room.getId());
        assertEquals(1, afterStart.size());

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/skip", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("BREAK"));

        List<StudyTask> afterBreak = studyTaskRepository.findByRoom_IdOrderByUser_IdAscSortOrderAsc(room.getId());
        assertEquals(1, afterBreak.size());

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/skip", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("WORK"));

        List<StudyTask> afterSecondWork = studyTaskRepository.findByRoom_IdOrderByUser_IdAscSortOrderAsc(room.getId());
        assertEquals(2, afterSecondWork.size());
        assertEquals("MotivBot", afterSecondWork.getFirst().getUser().getName());

        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/topic/room/" + room.getId() + "/tasks"),
                argThat((StudyTaskWsEvent event) -> event.getType() == StudyTaskWsEventType.BOT_GOAL_SUGGESTED)
        );
    }

    @Test
    @DisplayName("POST activate - не участник комнаты получает 400")
    void activateBot_nonParticipant_forbidden() throws Exception {
        Room room = createStudyRoom();

        mockMvc.perform(post("/api/rooms/{roomId}/bots/motivational-goals/activate", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCount\": 1, \"autoSuggest\": false, \"suggestOnBreak\": true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST activate - goalCount вне диапазона возвращает 400")
    void activateBot_goalCountOutOfRange_badRequest() throws Exception {
        Room room = createStudyRoom();
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/bots/motivational-goals/activate", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCount\": 0, \"autoSuggest\": true, \"suggestOnBreak\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("goalCount")));
    }

    @Test
    @DisplayName("Если autoSuggest=false, помодоро не создаёт цели бота")
    void pomodoro_noGenerationWhenAutoSuggestDisabled() throws Exception {
        Room room = createStudyRoom();
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/bots/motivational-goals/activate", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCount\": 1, \"autoSuggest\": false, \"suggestOnBreak\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/start", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDuration\": 120, \"breakDuration\": 60, \"roundsTotal\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("WORK"));

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/skip", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("BREAK"));

        List<StudyTask> tasksInRoom = studyTaskRepository.findByRoom_IdOrderByUser_IdAscSortOrderAsc(room.getId());
        assertEquals(0, tasksInRoom.size());

        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/room/" + room.getId() + "/tasks"),
                argThat((StudyTaskWsEvent event) -> event.getType() == StudyTaskWsEventType.BOT_GOAL_SUGGESTED)
        );
    }

    @Test
    @DisplayName("DELETE pomodoro очищает цели MotivBot в комнате")
    void stopPomodoro_clearsBotGoals() throws Exception {
        Room room = createStudyRoom();
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/bots/motivational-goals/activate", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCount\": 2, \"autoSuggest\": true, \"suggestOnBreak\": true}"))
                .andExpect(status().isOk());
        assertEquals(0, studyTaskRepository.findByRoom_IdOrderByUser_IdAscSortOrderAsc(room.getId()).size());

        mockMvc.perform(post("/api/rooms/{roomId}/pomodoro/start", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isCreated());
        assertEquals(2, studyTaskRepository.findByRoom_IdOrderByUser_IdAscSortOrderAsc(room.getId()).size());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/rooms/{roomId}/pomodoro", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isNoContent());

        assertEquals(0, studyTaskRepository.findByRoom_IdOrderByUser_IdAscSortOrderAsc(room.getId()).size());
    }

    @Test
    @DisplayName("Можно активировать двух мотивационных ботов и увидеть их в списке")
    void activateTwoBots_listContainsBoth() throws Exception {
        Room room = createStudyRoom();
        addParticipant(room, user);

        mockMvc.perform(post("/api/rooms/{roomId}/bots/motivational-goals/activate", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCount\": 1, \"autoSuggest\": false, \"suggestOnBreak\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/{roomId}/bots/motivational-goals/activate", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCount\": 2, \"autoSuggest\": false, \"suggestOnBreak\": false}"))
                .andExpect(status().isOk());

        String botsResponse = mockMvc.perform(get("/api/rooms/{roomId}/bots", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var bots = objectMapper.readTree(botsResponse);
        assertEquals(3, bots.size());
        int motivational = 0;
        int pomodoroManager = 0;
        for (var bot : bots) {
            String type = bot.get("botType").asText();
            if ("MOTIVATIONAL_GOALS".equals(type)) motivational++;
            if ("POMODORO_MANAGER".equals(type)) pomodoroManager++;
        }
        assertEquals(2, motivational);
        assertEquals(1, pomodoroManager);
    }

    @Test
    @DisplayName("Можно обновить конфиг и удалить конкретного бота")
    void updateAndDeleteSpecificBot() throws Exception {
        Room room = createStudyRoom();
        addParticipant(room, user);

        String activateResponse = mockMvc.perform(post("/api/rooms/{roomId}/bots/motivational-goals/activate", room.getId())
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCount\": 1, \"autoSuggest\": false, \"suggestOnBreak\": true}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String botId = objectMapper.readTree(activateResponse).get("botId").asText();

        mockMvc.perform(put("/api/rooms/{roomId}/bots/{botId}/config", room.getId(), botId)
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalCount\": 5, \"autoSuggest\": false, \"suggestOnBreak\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config").value(containsString("\"goalCount\":5")))
                .andExpect(jsonPath("$.config").value(containsString("\"suggestOnBreak\":false")));

        mockMvc.perform(delete("/api/rooms/{roomId}/bots/{botId}", room.getId(), botId)
                        .header("Authorization", auth()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/{roomId}/bots", room.getId())
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].botType").value("POMODORO_MANAGER"));
    }

}
