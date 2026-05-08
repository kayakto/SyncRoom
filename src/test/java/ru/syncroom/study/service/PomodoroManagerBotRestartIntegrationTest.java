package ru.syncroom.study.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.rooms.service.RoomChatService;
import ru.syncroom.study.domain.PomodoroSession;
import ru.syncroom.study.domain.RoomBot;
import ru.syncroom.study.dto.PomodoroManagerConfigRequest;
import ru.syncroom.study.repository.PomodoroSessionRepository;
import ru.syncroom.study.repository.RoomBotRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Pomodoro Manager: персистентный auto-restart после FINISHED")
class PomodoroManagerBotRestartIntegrationTest {

    private static final String MANAGER_CONFIG_LONG_DELAY = """
            {"autoRestart":true,"restartDelay":3600,"autoStart":false,
            "sendReminders":false,"sendMotivation":false,
            "workDuration":120,"breakDuration":30,"longBreakDuration":40,"roundsTotal":2}
            """;

    private static final String MANAGER_CONFIG_SHORT_DELAY = """
            {"autoRestart":true,"restartDelay":1,"autoStart":false,
            "sendReminders":false,"sendMotivation":false,
            "workDuration":120,"breakDuration":30,"longBreakDuration":40,"roundsTotal":2}
            """;

    private static final String MANAGER_CONFIG_SHORT_DELAY_TINY = """
            {"autoRestart":true,"restartDelay":1,"autoStart":false,
            "sendReminders":false,"sendMotivation":false,
            "workDuration":30,"breakDuration":10,"longBreakDuration":15,"roundsTotal":1}
            """;

    private static final String MANAGER_CONFIG_NO_RESTART = """
            {"autoRestart":false,"restartDelay":3600,"autoStart":false,
            "sendReminders":false,"sendMotivation":false,
            "workDuration":120,"breakDuration":30,"longBreakDuration":40,"roundsTotal":2}
            """;

    @Autowired
    private PomodoroManagerBotService pomodoroManagerBotService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PomodoroSessionRepository pomodoroSessionRepository;

    @Autowired
    private RoomBotRepository roomBotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomParticipantRepository participantRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private RoomChatService roomChatService;

    private User user;
    private User pomodoroBot;
    private Room room;

    @BeforeEach
    void setUp() {
        purgeAll();

        user = userRepository.save(User.builder()
                .name("PM Restart User")
                .email("pm-restart@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());

        pomodoroBot = userRepository.save(User.builder()
                .name("PomodoroBot")
                .email("pomodorobot@syncroom.local")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());

        room = roomRepository.save(Room.builder()
                .context("study")
                .title("Study PM")
                .maxParticipants(10)
                .isActive(true)
                .build());

        participantRepository.save(RoomParticipant.builder().room(room).user(user).build());
    }

    @AfterEach
    void tearDown() {
        purgeAll();
    }

    private void purgeAll() {
        pomodoroSessionRepository.deleteAll();
        roomBotRepository.deleteAll();
        participantRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void saveActiveManager(String configJson) {
        roomBotRepository.save(RoomBot.builder()
                .room(room)
                .botType("POMODORO_MANAGER")
                .isActive(true)
                .config(configJson)
                .build());
    }

    private PomodoroSession finishedSessionWithoutRestart() {
        return PomodoroSession.builder()
                .room(room)
                .startedBy(pomodoroBot)
                .phase("FINISHED")
                .workDuration(120)
                .breakDuration(30)
                .longBreakDuration(40)
                .roundsTotal(2)
                .currentRound(2)
                .phaseEndAt(null)
                .nextRestartAt(null)
                .build();
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("FINISHED lifecycle → в БД сохраняется next_restart_at (персистентный таймер)")
    void finishedEvent_persistsNextRestartAt() {
        saveActiveManager(MANAGER_CONFIG_LONG_DELAY);
        pomodoroSessionRepository.save(finishedSessionWithoutRestart());
        pomodoroSessionRepository.flush();

        eventPublisher.publishEvent(PomodoroLifecycleEvent.builder()
                .roomId(room.getId())
                .type(PomodoroLifecycleEvent.Type.FINISHED)
                .phase("FINISHED")
                .currentRound(2)
                .build());

        pomodoroSessionRepository.flush();
        PomodoroSession s = pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow();
        assertNotNull(s.getNextRestartAt(), "next_restart_at должен быть задан для рестарта после редеплоя");
        assertTrue(s.getNextRestartAt().isAfter(OffsetDateTime.now()));
        assertTrue(s.getNextRestartAt().isBefore(OffsetDateTime.now().plusHours(2)));
        verify(roomChatService).sendSystemBotMessage(eq(room.getId()), eq(pomodoroBot.getId()), any());
    }

    @Test
    @Order(15)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("activate менеджера при уже существующей FINISHED-сессии планирует рестарт и стартует новую WORK")
    void activateOnStuckFinished_schedulesAndRestarts() throws InterruptedException {
        pomodoroSessionRepository.save(finishedSessionWithoutRestart());

        PomodoroManagerConfigRequest req = new PomodoroManagerConfigRequest();
        req.setAutoStart(false);
        req.setAutoRestart(true);
        req.setRestartDelay(1);
        req.setRoundsTotal(1);
        req.setWorkDuration(30);
        req.setBreakDuration(10);
        req.setLongBreakDuration(15);
        req.setSendReminders(false);
        req.setSendMotivation(false);

        pomodoroManagerBotService.activate(room.getId(), user.getId(), req);

        Thread.sleep(3500);

        PomodoroSession s = pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow();
        assertEquals("WORK", s.getPhase());
        assertEquals(30, s.getWorkDuration());
        assertEquals(10, s.getBreakDuration());
        assertEquals(15, s.getLongBreakDuration());
        assertEquals(1, s.getRoundsTotal());
        assertNull(s.getNextRestartAt());
    }

    @Test
    @Order(20)
    @Transactional
    @DisplayName("autoRestart=false → next_restart_at не выставляется")
    void finishedEvent_autoRestartOff_skipsPersist() {
        saveActiveManager(MANAGER_CONFIG_NO_RESTART);
        pomodoroSessionRepository.save(finishedSessionWithoutRestart());
        pomodoroSessionRepository.flush();

        eventPublisher.publishEvent(PomodoroLifecycleEvent.builder()
                .roomId(room.getId())
                .type(PomodoroLifecycleEvent.Type.FINISHED)
                .phase("FINISHED")
                .currentRound(2)
                .build());

        pomodoroSessionRepository.flush();
        assertNull(pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow().getNextRestartAt());
    }

    @Test
    @Order(30)
    @Transactional
    @DisplayName("recoverRestartTimers: просроченный next_restart_at → сразу новая сессия WORK")
    void recoverOverdueStartsNewWorkSession() {
        saveActiveManager(MANAGER_CONFIG_LONG_DELAY);
        assertEquals(0, pomodoroSessionRepository.count(), "не должно быть сессии до явного save FINISHED");
        PomodoroSession finished = finishedSessionWithoutRestart();
        finished.setNextRestartAt(OffsetDateTime.now().minusMinutes(5));
        pomodoroSessionRepository.save(finished);
        pomodoroSessionRepository.flush();

        pomodoroManagerBotService.recoverRestartTimers();

        PomodoroSession s = pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow();
        assertEquals("WORK", s.getPhase());
        assertNull(s.getNextRestartAt());
    }

    @Test
    @Order(40)
    @Transactional
    @DisplayName("deactivate менеджера сбрасывает next_restart_at в БД")
    void deactivate_clearsPersistedRestart() {
        saveActiveManager(MANAGER_CONFIG_LONG_DELAY);
        PomodoroSession finished = finishedSessionWithoutRestart();
        finished.setNextRestartAt(OffsetDateTime.now().plusHours(1));
        pomodoroSessionRepository.save(finished);
        pomodoroSessionRepository.flush();

        pomodoroManagerBotService.deactivate(room.getId(), user.getId());

        assertNull(pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow().getNextRestartAt());
        assertTrue(roomBotRepository.findByRoom_IdAndBotTypeAndIsActiveTrue(room.getId(), "POMODORO_MANAGER").isEmpty());
    }

    @Test
    @Order(50)
    @Transactional
    @DisplayName("recoverRestartTimers без активного менеджера → next_restart_at очищается")
    void recoverWithoutActiveManager_clearsNextRestart() {
        saveActiveManager(MANAGER_CONFIG_LONG_DELAY);
        RoomBot bot = roomBotRepository.findByRoom_Id(room.getId()).getFirst();
        bot.setIsActive(false);
        roomBotRepository.save(bot);

        PomodoroSession finished = finishedSessionWithoutRestart();
        finished.setNextRestartAt(OffsetDateTime.now().plusMinutes(10));
        pomodoroSessionRepository.save(finished);
        pomodoroSessionRepository.flush();

        pomodoroManagerBotService.recoverRestartTimers();

        assertNull(pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow().getNextRestartAt());
    }

    @Test
    @Order(60)
    @Transactional
    @DisplayName("findByPhaseAndNextRestartAtIsNotNull находит только FINISHED с таймером")
    void repository_findFinishedWithRestart() {
        saveActiveManager(MANAGER_CONFIG_LONG_DELAY);
        Room otherRoom = roomRepository.save(Room.builder()
                .context("study")
                .title("Other")
                .maxParticipants(5)
                .isActive(true)
                .build());
        assertTrue(!otherRoom.getId().equals(room.getId()));

        PomodoroSession finished = finishedSessionWithoutRestart();
        finished.setNextRestartAt(OffsetDateTime.now().plusHours(1));
        pomodoroSessionRepository.save(finished);
        pomodoroSessionRepository.save(PomodoroSession.builder()
                .room(otherRoom)
                .startedBy(pomodoroBot)
                .phase("WORK")
                .workDuration(60)
                .breakDuration(10)
                .longBreakDuration(15)
                .roundsTotal(2)
                .currentRound(1)
                .phaseEndAt(OffsetDateTime.now().plusMinutes(20))
                .nextRestartAt(null)
                .build());
        pomodoroSessionRepository.flush();

        List<PomodoroSession> pending = pomodoroSessionRepository.findByPhaseAndNextRestartAtIsNotNull("FINISHED");
        assertEquals(1, pending.size());
        assertEquals(room.getId(), pending.getFirst().getRoom().getId());
    }

    /**
     * Отдельная транзакция: отложенный {@code schedule} выполняется в другом потоке и должен видеть закоммиченные данные.
     */
    @Test
    @Order(100)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Через ~1 с после FINISHED отложенный рестарт поднимает новую сессию WORK (как после редеплоя)")
    void delayedScheduledRestart_startsWorkSession() throws InterruptedException {
        saveActiveManager(MANAGER_CONFIG_SHORT_DELAY);
        pomodoroSessionRepository.save(finishedSessionWithoutRestart());
        pomodoroSessionRepository.flush();

        eventPublisher.publishEvent(PomodoroLifecycleEvent.builder()
                .roomId(room.getId())
                .type(PomodoroLifecycleEvent.Type.FINISHED)
                .phase("FINISHED")
                .currentRound(2)
                .build());

        Thread.sleep(3500);

        PomodoroSession s = pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow();
        assertEquals("WORK", s.getPhase());
        assertNull(s.getNextRestartAt());
        verify(roomChatService, atLeastOnce()).sendSystemBotMessage(eq(room.getId()), eq(pomodoroBot.getId()), any());
    }
}
