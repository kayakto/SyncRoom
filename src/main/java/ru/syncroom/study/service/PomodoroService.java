package ru.syncroom.study.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.projector.dto.UserDto;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.study.domain.PomodoroSession;
import ru.syncroom.study.dto.PomodoroResponse;
import ru.syncroom.study.dto.PomodoroStartRequest;
import ru.syncroom.study.ws.PomodoroEvent;
import ru.syncroom.study.ws.PomodoroEventType;
import ru.syncroom.study.repository.PomodoroSessionRepository;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PomodoroService {

    private static final String TOPIC_TEMPLATE = "/topic/room/%s/pomodoro";

    private final PomodoroSessionRepository pomodoroRepo;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PomodoroTimerService timerService;

    private String topic(UUID roomId) {
        return String.format(TOPIC_TEMPLATE, roomId);
    }

    private void publish(UUID roomId, PomodoroEventType type, Object payload) {
        messagingTemplate.convertAndSend(topic(roomId), PomodoroEvent.of(type, payload));
    }

    private UserDto toUserDto(User u) {
        return UserDto.builder()
                .id(u.getId().toString())
                .name(u.getName())
                .avatarUrl(u.getAvatarUrl())
                .build();
    }

    private PomodoroResponse toResponse(PomodoroSession s) {
        String phaseEndAt = s.getPhaseEndAt() != null
                ? s.getPhaseEndAt().withOffsetSameInstant(ZoneOffset.UTC).toString()
                : null;

        return PomodoroResponse.builder()
                .id(s.getId().toString())
                .roomId(s.getRoom().getId().toString())
                .startedBy(toUserDto(s.getStartedBy()))
                .phase(s.getPhase())
                .currentRound(s.getCurrentRound())
                .roundsTotal(s.getRoundsTotal())
                .phaseEndAt(phaseEndAt)
                .workDuration(s.getWorkDuration())
                .breakDuration(s.getBreakDuration())
                .longBreakDuration(s.getLongBreakDuration())
                .build();
    }

    @Transactional(readOnly = true)
    public PomodoroResponse get(UUID roomId) {
        PomodoroSession session = pomodoroRepo.findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("Pomodoro is not running in this room"));
        return toResponse(session);
    }

    @Transactional
    public PomodoroResponse start(UUID roomId, UUID userId, PomodoroStartRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

        if (!"study".equals(room.getContext())) {
            throw new BadRequestException("Pomodoro is only available for rooms with context='study'");
        }

        if (!participantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BadRequestException("User is not a participant of this room");
        }

        if (pomodoroRepo.findByRoomId(roomId).isPresent()) {
            throw new BadRequestException("Pomodoro is already running in this room");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        int work = request.getWorkDuration() != null ? request.getWorkDuration() : 1500;
        int brk = request.getBreakDuration() != null ? request.getBreakDuration() : 300;
        int longBrk = request.getLongBreakDuration() != null ? request.getLongBreakDuration() : 900;
        int rounds = request.getRoundsTotal() != null ? request.getRoundsTotal() : 4;

        OffsetDateTime phaseEndAt = OffsetDateTime.now().plusSeconds(work);

        PomodoroSession session = PomodoroSession.builder()
                .room(room)
                .startedBy(user)
                .phase("WORK")
                .workDuration(work)
                .breakDuration(brk)
                .longBreakDuration(longBrk)
                .roundsTotal(rounds)
                .currentRound(1)
                .phaseEndAt(phaseEndAt)
                .build();

        PomodoroSession saved = pomodoroRepo.save(session);

        timerService.schedulePhaseEnd(roomId, work, () -> skip(roomId));

        PomodoroResponse response = toResponse(saved);
        publish(roomId, PomodoroEventType.POMODORO_STARTED, response);

        log.debug("Pomodoro started in room {} by user {}", roomId, userId);
        return response;
    }

    @Transactional
    public PomodoroResponse pause(UUID roomId) {
        PomodoroSession session = pomodoroRepo.findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("Pomodoro is not running in this room"));

        if ("PAUSED".equals(session.getPhase()) || "FINISHED".equals(session.getPhase())) {
            throw new BadRequestException("Pomodoro is not active");
        }

        if (session.getPhaseEndAt() == null) {
            throw new BadRequestException("phaseEndAt is null");
        }

        long remainingSeconds = session.getPhaseEndAt().toEpochSecond() - OffsetDateTime.now().toEpochSecond();
        if (remainingSeconds < 0) remainingSeconds = 0;

        session.setPausedPhase(session.getPhase());
        session.setRemainingSeconds((int) remainingSeconds);
        session.setPhase("PAUSED");
        session.setPhaseEndAt(null);
        pomodoroRepo.save(session);

        timerService.cancel(roomId);

        publish(roomId, PomodoroEventType.POMODORO_PAUSED,
                Map.of("phase", session.getPhase(), "remainingSeconds", remainingSeconds));

        return toResponse(session);
    }

    @Transactional
    public PomodoroResponse resume(UUID roomId) {
        PomodoroSession session = pomodoroRepo.findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("Pomodoro is not running in this room"));

        if (!"PAUSED".equals(session.getPhase())) {
            throw new BadRequestException("Pomodoro is not paused");
        }

        if (session.getRemainingSeconds() == null || session.getPausedPhase() == null) {
            throw new BadRequestException("Pomodoro pause state is incomplete");
        }

        long remaining = session.getRemainingSeconds();
        OffsetDateTime newEnd = OffsetDateTime.now().plusSeconds(remaining);
        session.setPhase(session.getPausedPhase());
        session.setPhaseEndAt(newEnd);
        session.setPausedPhase(null);
        session.setRemainingSeconds(null);
        pomodoroRepo.save(session);

        timerService.schedulePhaseEnd(roomId, remaining, () -> skip(roomId));

        publish(roomId, PomodoroEventType.POMODORO_RESUMED,
                Map.of("phase", session.getPhase(), "phaseEndAt", newEnd.toInstant().toString()));

        return toResponse(session);
    }

    /**
     * Вызывается из {@link ru.syncroom.study.schedule.PomodoroPhaseScheduler} и из {@link PomodoroTimerService},
     * когда {@code phaseEndAt} уже не в будущем.
     */
    @Transactional
    public void advancePhaseIfExpired(UUID roomId) {
        Optional<PomodoroSession> opt = pomodoroRepo.findByRoomIdForUpdate(roomId);
        if (opt.isEmpty()) {
            return;
        }
        PomodoroSession session = opt.get();
        String phase = session.getPhase();
        if ("PAUSED".equals(phase) || "FINISHED".equals(phase)) {
            return;
        }
        if (session.getPhaseEndAt() == null) {
            return;
        }
        if (session.getPhaseEndAt().isAfter(OffsetDateTime.now())) {
            return;
        }
        transitionToNextPhase(session);
    }

    @Transactional
    public PomodoroResponse skip(UUID roomId) {
        PomodoroSession session = pomodoroRepo.findByRoomIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Pomodoro is not running in this room"));

        String current = session.getPhase();
        if ("PAUSED".equals(current) || "FINISHED".equals(current)) {
            throw new BadRequestException("Cannot skip phase: " + current);
        }
        return transitionToNextPhase(session);
    }

    private PomodoroResponse transitionToNextPhase(PomodoroSession session) {
        UUID roomId = session.getRoom().getId();
        String current = session.getPhase();
        int currentRound = session.getCurrentRound();
        int total = session.getRoundsTotal();

        String nextPhase;
        int nextRound = currentRound;
        int durationSeconds;

        switch (current) {
            case "WORK" -> {
                if (currentRound < total) {
                    nextPhase = "BREAK";
                    durationSeconds = session.getBreakDuration();
                } else {
                    nextPhase = "LONG_BREAK";
                    durationSeconds = session.getLongBreakDuration();
                }
            }
            case "BREAK" -> {
                nextPhase = "WORK";
                nextRound = currentRound + 1;
                durationSeconds = session.getWorkDuration();
            }
            case "LONG_BREAK" -> {
                nextPhase = "FINISHED";
                durationSeconds = 0;
            }
            default -> throw new BadRequestException("Cannot skip phase: " + current);
        }

        session.setPhase(nextPhase);
        session.setCurrentRound(nextRound);
        if ("FINISHED".equals(nextPhase)) {
            session.setPhaseEndAt(null);
            timerService.cancel(roomId);
        } else {
            session.setPhaseEndAt(OffsetDateTime.now().plusSeconds(durationSeconds));
            timerService.schedulePhaseEnd(roomId, durationSeconds, () -> skip(roomId));
        }

        pomodoroRepo.save(session);

        if ("FINISHED".equals(nextPhase)) {
            publish(roomId, PomodoroEventType.POMODORO_STOPPED, Map.of());
        } else {
            publish(roomId, PomodoroEventType.POMODORO_PHASE_CHANGED,
                    Map.of("phase", nextPhase,
                            "currentRound", nextRound,
                            "phaseEndAt", session.getPhaseEndAt().toInstant().toString()));
        }

        return toResponse(session);
    }

    @Transactional
    public void stop(UUID roomId) {
        pomodoroRepo.deleteByRoomId(roomId);
        publish(roomId, PomodoroEventType.POMODORO_STOPPED, Map.of());
        timerService.cancel(roomId);
    }
}

