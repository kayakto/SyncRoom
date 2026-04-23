package ru.syncroom.study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.study.domain.BotGoalTemplate;
import ru.syncroom.study.domain.RoomBot;
import ru.syncroom.study.domain.StudyTask;
import ru.syncroom.study.dto.ActivateMotivationalBotRequest;
import ru.syncroom.study.dto.RoomBotResponse;
import ru.syncroom.study.repository.BotGoalTemplateRepository;
import ru.syncroom.study.repository.RoomBotRepository;
import ru.syncroom.study.repository.StudyTaskRepository;
import ru.syncroom.study.ws.StudyTaskWsEvent;
import ru.syncroom.study.ws.StudyTaskWsEventType;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MotivationalGoalBotService {

    private static final String TASKS_TOPIC = "/topic/room/%s/tasks";
    private static final String MOTIVATIONAL_BOT_TYPE = "MOTIVATIONAL_GOALS";
    private static final String BOT_EMAIL = "motivbot@syncroom.local";
    private static final String BOT_NAME = "MotivBot";

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final RoomBotRepository roomBotRepository;
    private final BotGoalTemplateRepository templateRepository;
    private final StudyTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public RoomBotResponse activate(UUID roomId, UUID userId, ActivateMotivationalBotRequest request) {
        Room room = requireRoom(roomId);
        requireParticipant(roomId, userId);

        int goalCount = normalizeGoalCount(request != null ? request.getGoalCount() : null);
        boolean autoSuggest = request == null || request.getAutoSuggest() == null || request.getAutoSuggest();
        boolean suggestOnBreak = request == null || request.getSuggestOnBreak() == null || request.getSuggestOnBreak();

        String config = writeConfig(goalCount, autoSuggest, suggestOnBreak);
        RoomBot bot = RoomBot.builder()
                .room(room)
                .botType(MOTIVATIONAL_BOT_TYPE)
                .isActive(true)
                .config(config)
                .build();
        RoomBot saved = roomBotRepository.save(bot);
        return toResponse(saved);
    }

    @Transactional
    public RoomBotResponse deactivate(UUID roomId, UUID userId) {
        requireRoom(roomId);
        requireParticipant(roomId, userId);
        RoomBot bot = roomBotRepository.findFirstByRoom_IdAndBotTypeOrderByCreatedAtDesc(roomId, MOTIVATIONAL_BOT_TYPE)
                .orElseThrow(() -> new NotFoundException("Motivational bot is not configured for this room"));
        bot.setIsActive(false);
        return toResponse(roomBotRepository.save(bot));
    }

    @Transactional
    public RoomBotResponse updateConfig(UUID roomId, UUID userId, UUID botId, ActivateMotivationalBotRequest request) {
        requireRoom(roomId);
        requireParticipant(roomId, userId);
        RoomBot bot = roomBotRepository.findByIdAndRoom_Id(botId, roomId)
                .orElseThrow(() -> new NotFoundException("Room bot not found"));
        int goalCount = normalizeGoalCount(request != null ? request.getGoalCount() : null);
        boolean autoSuggest = request == null || request.getAutoSuggest() == null || request.getAutoSuggest();
        boolean suggestOnBreak = request == null || request.getSuggestOnBreak() == null || request.getSuggestOnBreak();
        bot.setConfig(writeConfig(goalCount, autoSuggest, suggestOnBreak));
        return toResponse(roomBotRepository.save(bot));
    }

    @Transactional
    public void delete(UUID roomId, UUID userId, UUID botId) {
        requireRoom(roomId);
        requireParticipant(roomId, userId);
        RoomBot bot = roomBotRepository.findByIdAndRoom_Id(botId, roomId)
                .orElseThrow(() -> new NotFoundException("Room bot not found"));
        roomBotRepository.delete(bot);
    }

    @Transactional(readOnly = true)
    public List<RoomBotResponse> list(UUID roomId, UUID userId) {
        requireRoom(roomId);
        requireParticipant(roomId, userId);
        return roomBotRepository.findByRoom_Id(roomId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void suggestOnBreak(UUID roomId) {
        // Legacy compatibility hook. New behavior is round-based suggestion in WORK phase.
        suggestOnRoundStart(roomId);
    }

    @Transactional
    public void suggestOnPomodoroStarted(UUID roomId) {
        List<RoomBot> activeBots = roomBotRepository.findByRoom_IdAndBotTypeAndIsActiveTrue(roomId, MOTIVATIONAL_BOT_TYPE);
        for (RoomBot bot : activeBots) {
            Map<String, Object> cfg = readConfig(bot.getConfig());
            Object autoSuggestRaw = cfg.get("autoSuggest");
            boolean autoSuggest = !(autoSuggestRaw instanceof Boolean b) || b;
            if (!autoSuggest) {
                continue;
            }
            int goalCount = normalizeGoalCount(readInt(cfg.get("goalCount")));
            suggestGoalsInternal(bot.getRoom(), goalCount);
        }
    }

    @Transactional
    public void suggestOnRoundStart(UUID roomId) {
        List<RoomBot> activeBots = roomBotRepository.findByRoom_IdAndBotTypeAndIsActiveTrue(roomId, MOTIVATIONAL_BOT_TYPE);
        for (RoomBot bot : activeBots) {
            Map<String, Object> cfg = readConfig(bot.getConfig());
            Object autoSuggestRaw = cfg.get("autoSuggest");
            boolean autoSuggest = !(autoSuggestRaw instanceof Boolean b) || b;
            if (!autoSuggest) {
                continue;
            }
            // On each new WORK round create one fresh goal.
            suggestGoalsInternal(bot.getRoom(), 1);
        }
    }

    @Transactional
    public void clearBotGoalsInRoom(UUID roomId) {
        User botUser = userRepository.findByEmail(BOT_EMAIL).orElse(null);
        if (botUser == null) {
            return;
        }
        taskRepository.deleteByRoom_IdAndUser_Id(roomId, botUser.getId());
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

    private int normalizeGoalCount(Integer goalCount) {
        int value = goalCount == null ? 3 : goalCount;
        if (value < 1 || value > 10) {
            throw new BadRequestException("goalCount must be between 1 and 10");
        }
        return value;
    }

    private String normalizeContext(String raw) {
        if (raw == null || raw.isBlank()) {
            return "STUDY";
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "study" -> "STUDY";
            case "work" -> "WORK";
            case "sport" -> "SPORT";
            case "personal", "leisure" -> "LEISURE";
            default -> "STUDY";
        };
    }

    private void suggestGoalsInternal(Room room, int goalCount) {
        List<BotGoalTemplate> templates = new ArrayList<>(templateRepository.findByContextAndIsActiveTrue(
                normalizeContext(room.getContext())));
        if (templates.isEmpty()) {
            templates.addAll(templateRepository.findByContextAndIsActiveTrue("STUDY"));
        }
        if (templates.isEmpty()) {
            return;
        }

        Collections.shuffle(templates);
        int count = Math.min(goalCount, templates.size());
        User botUser = getOrCreateBotUser();

        int nextSortOrder = taskRepository.findByUserIdAndRoomIdOrderBySortOrderAsc(botUser.getId(), room.getId())
                .stream()
                .map(StudyTask::getSortOrder)
                .max(Integer::compareTo)
                .orElse(-1) + 1;

        for (int i = 0; i < count; i++) {
            StudyTask task = StudyTask.builder()
                    .user(botUser)
                    .room(room)
                    .text(templates.get(i).getText())
                    .isDone(false)
                    .sortOrder(nextSortOrder++)
                    .build();
            StudyTask savedTask = taskRepository.save(task);
            publishSuggestedGoal(room.getId(), savedTask, botUser);
        }
    }

    private void publishSuggestedGoal(UUID roomId, StudyTask task, User botUser) {
        Map<String, Object> payload = Map.of(
                "task", Map.of(
                        "id", task.getId().toString(),
                        "text", task.getText(),
                        "ownerId", botUser.getId().toString(),
                        "ownerName", botUser.getName(),
                        "isBot", true
                )
        );
        messagingTemplate.convertAndSend(
                String.format(TASKS_TOPIC, roomId),
                StudyTaskWsEvent.of(StudyTaskWsEventType.BOT_GOAL_SUGGESTED, payload)
        );
    }

    private User getOrCreateBotUser() {
        return userRepository.findByEmail(BOT_EMAIL)
                .orElseGet(() -> userRepository.save(User.builder()
                        .name(BOT_NAME)
                        .email(BOT_EMAIL)
                        .provider(AuthProvider.EMAIL)
                        .avatarUrl("/static/bots/motivbot.png")
                        .build()));
    }

    private String writeConfig(int goalCount, boolean autoSuggest, boolean suggestOnBreak) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "goalCount", goalCount,
                    "autoSuggest", autoSuggest,
                    "suggestOnBreak", suggestOnBreak
            ));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid bot config");
        }
    }

    private Map<String, Object> readConfig(String config) {
        if (config == null || config.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(config, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse room bot config: {}", config, e);
            return Map.of();
        }
    }

    private Integer readInt(Object value) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
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
