package ru.syncroom.study.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.study.domain.PomodoroSession;
import ru.syncroom.study.repository.PomodoroSessionRepository;
import ru.syncroom.study.ws.PomodoroEvent;
import ru.syncroom.study.ws.PomodoroEventType;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Pomodoro advancePhaseIfExpired (серверная смена фазы)")
class PomodoroAdvancePhaseTest {

    @Autowired
    private PomodoroService pomodoroService;

    @Autowired
    private PomodoroSessionRepository pomodoroSessionRepository;

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

    private User user;
    private Room room;

    @BeforeEach
    void setUp() {
        pomodoroSessionRepository.deleteAll();
        participantRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .name("P User")
                .email("padv@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());

        room = roomRepository.save(Room.builder()
                .context("study")
                .title("S")
                .maxParticipants(10)
                .isActive(true)
                .build());

        participantRepository.save(RoomParticipant.builder().room(room).user(user).build());
    }

    private PomodoroSession baseSession(String phase, OffsetDateTime phaseEndAt) {
        return PomodoroSession.builder()
                .room(room)
                .startedBy(user)
                .phase(phase)
                .workDuration(100)
                .breakDuration(50)
                .longBreakDuration(70)
                .roundsTotal(2)
                .currentRound(1)
                .phaseEndAt(phaseEndAt)
                .build();
    }

    @Test
    @DisplayName("WORK с истёкшим phaseEndAt → BREAK и новый phaseEndAt")
    void workExpired_goesToBreak() {
        pomodoroSessionRepository.save(baseSession("WORK", OffsetDateTime.now().minusSeconds(2)));

        pomodoroService.advancePhaseIfExpired(room.getId());

        PomodoroSession s = pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow();
        assertEquals("BREAK", s.getPhase());
        assertEquals(1, s.getCurrentRound());
        assertNotNull(s.getPhaseEndAt());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + room.getId() + "/pomodoro"),
                argThat((PomodoroEvent ev) -> ev.getType() == PomodoroEventType.POMODORO_PHASE_CHANGED));
    }

    @Test
    @DisplayName("WORK с phaseEndAt в будущем — без изменений")
    void workNotExpired_noOp() {
        OffsetDateTime end = OffsetDateTime.now().plusHours(1);
        pomodoroSessionRepository.save(baseSession("WORK", end));

        pomodoroService.advancePhaseIfExpired(room.getId());

        PomodoroSession s = pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow();
        assertEquals("WORK", s.getPhase());
        assertEquals(0, java.time.Duration.between(end, s.getPhaseEndAt()).abs().toSeconds());
    }

    @Test
    @DisplayName("PAUSED — не трогаем даже с прошлым phaseEndAt (в БД так не бывает, но защита)")
    void paused_skipped() {
        PomodoroSession s = baseSession("PAUSED", OffsetDateTime.now().minusSeconds(5));
        s.setPhaseEndAt(null);
        s.setPausedPhase("WORK");
        s.setRemainingSeconds(30);
        pomodoroSessionRepository.save(s);

        pomodoroService.advancePhaseIfExpired(room.getId());

        PomodoroSession after = pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow();
        assertEquals("PAUSED", after.getPhase());
    }

    @Test
    @DisplayName("findRoomIdsWithExpiredPhase находит комнату с истёкшей фазой")
    void repository_findExpiredRooms() {
        pomodoroSessionRepository.save(baseSession("WORK", OffsetDateTime.now().minusSeconds(1)));

        var ids = pomodoroSessionRepository.findRoomIdsWithExpiredPhase(OffsetDateTime.now());
        assertEquals(1, ids.size());
        assertEquals(room.getId(), ids.getFirst());
    }

    @Test
    @DisplayName("BREAK с истёкшим phaseEndAt → WORK, round+1 и POMODORO_PHASE_CHANGED")
    void breakExpired_goesToWorkNextRound() {
        pomodoroSessionRepository.save(PomodoroSession.builder()
                .room(room)
                .startedBy(user)
                .phase("BREAK")
                .workDuration(100)
                .breakDuration(50)
                .longBreakDuration(70)
                .roundsTotal(2)
                .currentRound(1)
                .phaseEndAt(OffsetDateTime.now().minusSeconds(1))
                .build());

        pomodoroService.advancePhaseIfExpired(room.getId());

        PomodoroSession s = pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow();
        assertEquals("WORK", s.getPhase());
        assertEquals(2, s.getCurrentRound());
        assertNotNull(s.getPhaseEndAt());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + room.getId() + "/pomodoro"),
                argThat((PomodoroEvent ev) -> ev.getType() == PomodoroEventType.POMODORO_PHASE_CHANGED));
    }

    @Test
    @DisplayName("LONG_BREAK с истёкшим phaseEndAt → FINISHED и POMODORO_STOPPED")
    void longBreakExpired_finishes() {
        pomodoroSessionRepository.save(PomodoroSession.builder()
                .room(room)
                .startedBy(user)
                .phase("LONG_BREAK")
                .workDuration(100)
                .breakDuration(50)
                .longBreakDuration(70)
                .roundsTotal(2)
                .currentRound(2)
                .phaseEndAt(OffsetDateTime.now().minusSeconds(1))
                .build());

        pomodoroService.advancePhaseIfExpired(room.getId());

        PomodoroSession s = pomodoroSessionRepository.findByRoomId(room.getId()).orElseThrow();
        assertEquals("FINISHED", s.getPhase());
        assertNull(s.getPhaseEndAt());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + room.getId() + "/pomodoro"),
                argThat((PomodoroEvent ev) -> ev.getType() == PomodoroEventType.POMODORO_STOPPED));
    }
}
