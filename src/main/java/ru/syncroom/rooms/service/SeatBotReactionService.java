package ru.syncroom.rooms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.rooms.config.SeatBotProperties;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomSeatBot;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.rooms.repository.RoomSeatBotRepository;
import ru.syncroom.rooms.seatbot.SeatBotKind;
import ru.syncroom.study.domain.BotGoalTemplate;
import ru.syncroom.study.domain.StudyTask;
import ru.syncroom.study.domain.TaskLike;
import ru.syncroom.study.event.HumanTaskCompletedEvent;
import ru.syncroom.study.repository.BotGoalTemplateRepository;
import ru.syncroom.study.repository.StudyTaskRepository;
import ru.syncroom.study.repository.TaskLikeRepository;
import ru.syncroom.study.service.PomodoroLifecycleEvent;
import ru.syncroom.study.ws.StudyTaskWsEvent;
import ru.syncroom.study.ws.StudyTaskWsEventType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatBotReactionService {

    private static final String TASKS_TOPIC = "/topic/room/%s/tasks";

    private final RoomSeatBotRepository roomSeatBotRepository;
    private final RoomRepository roomRepository;
    private final BotGoalTemplateRepository templateRepository;
    private final StudyTaskRepository studyTaskRepository;
    private final TaskLikeRepository taskLikeRepository;
    private final SeatBotProperties seatBotProperties;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    @Transactional
    public void onPomodoroLifecycle(PomodoroLifecycleEvent event) {
        UUID roomId = event.getRoomId();
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null || !"study".equalsIgnoreCase(room.getContext())) {
            return;
        }
        String phase = event.getPhase();
        if (phase == null) {
            return;
        }

        if (event.getType() == PomodoroLifecycleEvent.Type.STARTED && "WORK".equals(phase)) {
            maybeStudyHelperLikeDuringWork(roomId);
            return;
        }

        if (event.getType() == PomodoroLifecycleEvent.Type.PHASE_CHANGED) {
            if ("BREAK".equals(phase) || "LONG_BREAK".equals(phase)) {
                studyHelperSuggestOnBreak(roomId, room);
            } else if ("WORK".equals(phase)) {
                maybeStudyHelperLikeDuringWork(roomId);
            }
        }
    }


    @EventListener
    @Transactional
    public void onHumanTaskCompleted(HumanTaskCompletedEvent event) {
        StudyTask task = event.task();
        if (task.getUser() == null) {
            return;
        }
        Room room = task.getRoom();
        if (!"sport".equalsIgnoreCase(room.getContext())) {
            return;
        }
        UUID roomId = room.getId();
        List<RoomSeatBot> cheerBots = roomSeatBotRepository.findByRoom_IdAndBotType(roomId, SeatBotKind.SPORT_CHEERLEADER.getTypeKey());
        for (RoomSeatBot bot : cheerBots) {
            cheerLikeTask(bot, task);
        }
    }

    private void studyHelperSuggestOnBreak(UUID roomId, Room room) {
        List<RoomSeatBot> helpers = roomSeatBotRepository.findByRoom_IdAndBotType(roomId, SeatBotKind.STUDY_HELPER.getTypeKey());
        if (helpers.isEmpty()) {
            return;
        }
        List<BotGoalTemplate> templates = new ArrayList<>(templateRepository.findByContextAndIsActiveTrue(normalizeTemplateContext(room.getContext())));
        if (templates.isEmpty()) {
            templates.addAll(templateRepository.findByContextAndIsActiveTrue("STUDY"));
        }
        if (templates.isEmpty()) {
            return;
        }
        Collections.shuffle(templates);
        BotGoalTemplate tpl = templates.getFirst();
        for (RoomSeatBot bot : helpers) {
            int nextOrder = studyTaskRepository.maxSortOrderByOwnerSeatBot(bot.getId(), roomId) + 1;
            StudyTask saved = studyTaskRepository.save(StudyTask.builder()
                    .user(null)
                    .ownerSeatBot(bot)
                    .room(room)
                    .text(tpl.getText())
                    .isDone(false)
                    .sortOrder(nextOrder)
                    .build());
            publishBotGoalSuggested(roomId, saved, bot);
        }
    }

    private void maybeStudyHelperLikeDuringWork(UUID roomId) {
        List<RoomSeatBot> helpers = roomSeatBotRepository.findByRoom_IdAndBotType(roomId, SeatBotKind.STUDY_HELPER.getTypeKey());
        if (helpers.isEmpty()) {
            return;
        }
        double p = seatBotProperties.getStudyHelperWorkLikeProbability();
        if (p <= 0) {
            return;
        }
        for (RoomSeatBot bot : helpers) {
            if (ThreadLocalRandom.current().nextDouble() >= p) {
                continue;
            }
            List<StudyTask> candidates = new ArrayList<>(studyTaskRepository.findOpenHumanTasksInRoom(roomId));
            if (candidates.isEmpty()) {
                continue;
            }
            Collections.shuffle(candidates);
            StudyTask target = candidates.getFirst();
            if (target.getUser() == null) {
                continue;
            }
            if (taskLikeRepository.existsByTask_IdAndLikerSeatBot_Id(target.getId(), bot.getId())) {
                continue;
            }
            taskLikeRepository.save(TaskLike.builder()
                    .task(target)
                    .user(null)
                    .likerSeatBot(bot)
                    .build());
            long count = taskLikeRepository.countByTask_Id(target.getId());
            messagingTemplate.convertAndSend(
                    String.format(TASKS_TOPIC, roomId),
                    StudyTaskWsEvent.of(StudyTaskWsEventType.TASK_LIKED, Map.of(
                            "taskId", target.getId().toString(),
                            "userId", bot.getId().toString(),
                            "userName", bot.getName(),
                            "likeCount", count,
                            "action", "LIKE",
                            "isBot", true
                    )));
        }
    }

    private void cheerLikeTask(RoomSeatBot bot, StudyTask task) {
        UUID roomId = task.getRoom().getId();
        if (taskLikeRepository.existsByTask_IdAndLikerSeatBot_Id(task.getId(), bot.getId())) {
            return;
        }
        taskLikeRepository.save(TaskLike.builder()
                .task(task)
                .user(null)
                .likerSeatBot(bot)
                .build());
        long count = taskLikeRepository.countByTask_Id(task.getId());
        messagingTemplate.convertAndSend(
                String.format(TASKS_TOPIC, roomId),
                StudyTaskWsEvent.of(StudyTaskWsEventType.TASK_LIKED, Map.of(
                        "taskId", task.getId().toString(),
                        "userId", bot.getId().toString(),
                        "userName", bot.getName(),
                        "likeCount", count,
                        "action", "LIKE",
                        "isBot", true
                )));
    }

    private void publishBotGoalSuggested(UUID roomId, StudyTask task, RoomSeatBot bot) {
        Map<String, Object> payload = Map.of(
                "task", Map.of(
                        "id", task.getId().toString(),
                        "text", task.getText(),
                        "ownerId", bot.getId().toString(),
                        "ownerName", bot.getName(),
                        "isBot", true
                )
        );
        messagingTemplate.convertAndSend(
                String.format(TASKS_TOPIC, roomId),
                StudyTaskWsEvent.of(StudyTaskWsEventType.BOT_GOAL_SUGGESTED, payload)
        );
    }

    private String normalizeTemplateContext(String raw) {
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
}
