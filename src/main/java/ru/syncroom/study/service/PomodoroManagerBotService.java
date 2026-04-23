package ru.syncroom.study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.rooms.service.RoomChatService;
import ru.syncroom.study.domain.RoomBot;
import ru.syncroom.study.dto.PomodoroManagerConfigRequest;
import ru.syncroom.study.dto.PomodoroStartRequest;
import ru.syncroom.study.dto.RoomBotResponse;
import ru.syncroom.study.repository.PomodoroSessionRepository;
import ru.syncroom.study.repository.RoomBotRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PomodoroManagerBotService {

    private static final String BOT_TYPE = "POMODORO_MANAGER";
    private static final String BOT_EMAIL = "pomodorobot@syncroom.local";
    private static final String BOT_NAME = "PomodoroBot";

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final RoomBotRepository roomBotRepository;
    private final UserRepository userRepository;
    private final PomodoroSessionRepository pomodoroSessionRepository;
    private final PomodoroService pomodoroService;
    private final RoomChatService roomChatService;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<UUID, ScheduledFuture<?>> restartTimers = new ConcurrentHashMap<>();

    @Transactional
    public RoomBotResponse activate(UUID roomId, UUID userId, PomodoroManagerConfigRequest request) {
        Room room = requireRoom(roomId);
        requireParticipant(roomId, userId);
        RoomBot bot = RoomBot.builder()
                .room(room)
                .botType(BOT_TYPE)
                .isActive(true)
                .config(writeConfig(mergeWithDefaults(request)))
                .build();
        RoomBot saved = roomBotRepository.save(bot);
        scheduleAutostartIfNeeded(roomId, readConfig(saved.getConfig()));
        return toResponse(saved);
    }

    @Transactional
    public RoomBotResponse updateConfig(UUID roomId, UUID userId, UUID botId, PomodoroManagerConfigRequest request) {
        requireRoom(roomId);
        requireParticipant(roomId, userId);
        RoomBot bot = roomBotRepository.findByIdAndRoom_Id(botId, roomId)
                .orElseThrow(() -> new NotFoundException("Room bot not found"));
        if (!BOT_TYPE.equals(bot.getBotType())) {
            throw new BadRequestException("Bot is not a pomodoro manager");
        }
        bot.setConfig(writeConfig(mergeWithDefaults(request)));
        RoomBot saved = roomBotRepository.save(bot);
        scheduleAutostartIfNeeded(roomId, readConfig(saved.getConfig()));
        return toResponse(saved);
    }

    @Transactional
    public RoomBotResponse deactivate(UUID roomId, UUID userId) {
        requireRoom(roomId);
        requireParticipant(roomId, userId);
        RoomBot bot = roomBotRepository.findFirstByRoom_IdAndBotTypeOrderByCreatedAtDesc(roomId, BOT_TYPE)
                .orElseThrow(() -> new NotFoundException("Pomodoro manager bot is not configured for this room"));
        bot.setIsActive(false);
        cancelRestart(roomId);
        return toResponse(roomBotRepository.save(bot));
    }

    @Transactional
    public void deactivateAllInRoom(UUID roomId) {
        for (RoomBot bot : roomBotRepository.findByRoom_IdAndBotTypeAndIsActiveTrue(roomId, BOT_TYPE)) {
            bot.setIsActive(false);
            roomBotRepository.save(bot);
        }
        cancelRestart(roomId);
    }

    @Transactional
    public void ensureDefaultActive(UUID roomId) {
        if (!roomBotRepository.findByRoom_IdAndBotTypeAndIsActiveTrue(roomId, BOT_TYPE).isEmpty()) {
            return;
        }
        Room room = requireRoom(roomId);
        RoomBot saved = roomBotRepository.save(RoomBot.builder()
                .room(room)
                .botType(BOT_TYPE)
                .isActive(true)
                .config(writeConfig(defaultConfig()))
                .build());
        scheduleAutostartIfNeeded(roomId, readConfig(saved.getConfig()));
    }

    @EventListener
    @Transactional
    public void onPomodoroEvent(PomodoroLifecycleEvent event) {
        var bots = roomBotRepository.findByRoom_IdAndBotTypeAndIsActiveTrue(event.getRoomId(), BOT_TYPE);
        if (bots.isEmpty()) {
            return;
        }
        Map<String, Object> cfg = readConfig(bots.getLast().getConfig());
        switch (event.getType()) {
            case STARTED -> sendPhaseMessageIfNeeded(event.getRoomId(), "WORK", event.getCurrentRound(), cfg);
            case PHASE_CHANGED -> sendPhaseMessageIfNeeded(event.getRoomId(), event.getPhase(), event.getCurrentRound(), cfg);
            case FINISHED -> {
                sendPhaseMessageIfNeeded(event.getRoomId(), "FINISHED", event.getCurrentRound(), cfg);
                scheduleRestartIfNeeded(event.getRoomId(), cfg);
            }
        }
    }

    private void sendPhaseMessageIfNeeded(UUID roomId, String phase, Integer round, Map<String, Object> cfg) {
        boolean sendReminders = readBool(cfg.get("sendReminders"), true);
        boolean sendMotivation = readBool(cfg.get("sendMotivation"), true);
        if (!sendReminders && !sendMotivation) {
            return;
        }
        String text = switch (phase) {
            case "WORK" -> "Раунд " + (round != null ? round : 1) + " начался! Сфокусируйтесь на задачах.";
            case "BREAK" -> "Перерыв! Встаньте, потянитесь, выпейте воды.";
            case "LONG_BREAK" -> "Длинный перерыв! Отличная работа, отдохните как следует.";
            case "FINISHED" -> "Сессия завершена! Молодцы!";
            default -> null;
        };
        if (text == null) return;
        roomChatService.sendSystemBotMessage(roomId, getOrCreateBotUser().getId(), text);
    }

    private void scheduleAutostartIfNeeded(UUID roomId, Map<String, Object> cfg) {
        boolean autoStart = readBool(cfg.get("autoStart"), true);
        if (!autoStart || pomodoroSessionRepository.findByRoomId(roomId).isPresent()) {
            return;
        }
        int delay = readInt(cfg.get("autoStartDelay"), 10);
        scheduler.schedule(() -> startPomodoroIfAbsent(roomId, cfg), delay, TimeUnit.SECONDS);
    }

    private void scheduleRestartIfNeeded(UUID roomId, Map<String, Object> cfg) {
        boolean autoRestart = readBool(cfg.get("autoRestart"), true);
        if (!autoRestart) {
            return;
        }
        cancelRestart(roomId);
        int delay = readInt(cfg.get("restartDelay"), 300);
        roomChatService.sendSystemBotMessage(
                roomId,
                getOrCreateBotUser().getId(),
                "Новая сессия запустится через " + delay + " секунд."
        );
        ScheduledFuture<?> future = scheduler.schedule(() -> startPomodoroIfAbsent(roomId, cfg), delay, TimeUnit.SECONDS);
        restartTimers.put(roomId, future);
    }

    private void startPomodoroIfAbsent(UUID roomId, Map<String, Object> cfg) {
        try {
            PomodoroStartRequest request = new PomodoroStartRequest();
            request.setWorkDuration(readInt(cfg.get("workDuration"), 1500));
            request.setBreakDuration(readInt(cfg.get("breakDuration"), 300));
            request.setLongBreakDuration(readInt(cfg.get("longBreakDuration"), 900));
            request.setRoundsTotal(readInt(cfg.get("roundsTotal"), 4));
            pomodoroService.startByBot(roomId, getOrCreateBotUser().getId(), request);
        } catch (Exception e) {
            log.warn("Pomodoro manager bot failed to auto-start in room {}: {}", roomId, e.getMessage());
        }
    }

    private void cancelRestart(UUID roomId) {
        ScheduledFuture<?> old = restartTimers.remove(roomId);
        if (old != null) old.cancel(false);
    }

    private Room requireRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
    }

    private void requireParticipant(UUID roomId, UUID userId) {
        if (!participantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BadRequestException("User is not a participant of this room");
        }
    }

    private User getOrCreateBotUser() {
        return userRepository.findByEmail(BOT_EMAIL)
                .orElseGet(() -> userRepository.save(User.builder()
                        .name(BOT_NAME)
                        .email(BOT_EMAIL)
                        .provider(AuthProvider.EMAIL)
                        .avatarUrl("/static/bots/pomodorobot.png")
                        .build()));
    }

    private Map<String, Object> mergeWithDefaults(PomodoroManagerConfigRequest req) {
        Map<String, Object> cfg = defaultConfig();
        if (req == null) return cfg;
        if (req.getAutoStart() != null) cfg.put("autoStart", req.getAutoStart());
        if (req.getAutoStartDelay() != null) cfg.put("autoStartDelay", req.getAutoStartDelay());
        if (req.getAutoRestart() != null) cfg.put("autoRestart", req.getAutoRestart());
        if (req.getRestartDelay() != null) cfg.put("restartDelay", req.getRestartDelay());
        if (req.getWorkDuration() != null) cfg.put("workDuration", req.getWorkDuration());
        if (req.getBreakDuration() != null) cfg.put("breakDuration", req.getBreakDuration());
        if (req.getLongBreakDuration() != null) cfg.put("longBreakDuration", req.getLongBreakDuration());
        if (req.getRoundsTotal() != null) cfg.put("roundsTotal", req.getRoundsTotal());
        if (req.getSendReminders() != null) cfg.put("sendReminders", req.getSendReminders());
        if (req.getSendMotivation() != null) cfg.put("sendMotivation", req.getSendMotivation());
        return cfg;
    }

    private Map<String, Object> defaultConfig() {
        return new java.util.HashMap<>(Map.of(
                "autoStart", true,
                "autoStartDelay", 10,
                "autoRestart", true,
                "restartDelay", 300,
                "workDuration", 1500,
                "breakDuration", 300,
                "longBreakDuration", 900,
                "roundsTotal", 4,
                "sendReminders", true,
                "sendMotivation", true
        ));
    }

    private String writeConfig(Map<String, Object> cfg) {
        try {
            return objectMapper.writeValueAsString(cfg);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid pomodoro manager config");
        }
    }

    private Map<String, Object> readConfig(String config) {
        if (config == null || config.isBlank()) return defaultConfig();
        try {
            return objectMapper.readValue(config, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return defaultConfig();
        }
    }

    private int readInt(Object value, int def) {
        if (value instanceof Number n) return n.intValue();
        return def;
    }

    private boolean readBool(Object value, boolean def) {
        if (value instanceof Boolean b) return b;
        return def;
    }

    private RoomBotResponse toResponse(RoomBot bot) {
        return RoomBotResponse.builder()
                .botId(bot.getId().toString())
                .botType(bot.getBotType())
                .isActive(Boolean.TRUE.equals(bot.getIsActive()))
                .config(bot.getConfig())
                .build();
    }
}
