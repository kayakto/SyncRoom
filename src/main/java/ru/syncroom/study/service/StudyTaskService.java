package ru.syncroom.study.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.study.domain.StudyTask;
import ru.syncroom.study.domain.TaskLike;
import ru.syncroom.study.dto.*;
import ru.syncroom.study.repository.StudyTaskRepository;
import ru.syncroom.study.repository.TaskLikeRepository;
import ru.syncroom.study.ws.StudyTaskWsEvent;
import ru.syncroom.study.ws.StudyTaskWsEventType;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyTaskService {

    private static final String TASKS_TOPIC = "/topic/room/%s/tasks";
    private static final String LEADERBOARD_FORBIDDEN_CONTEXT = "leisure";

    private final StudyTaskRepository taskRepository;
    private final TaskLikeRepository likeRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private void publishTaskEvent(UUID roomId, StudyTaskWsEventType type, Object payload) {
        messagingTemplate.convertAndSend(
                String.format(TASKS_TOPIC, roomId),
                StudyTaskWsEvent.of(type, payload));
    }

    private void requireParticipant(UUID roomId, UUID userId) {
        if (!participantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BadRequestException("User is not a participant of this room");
        }
    }

    private StudyTask requireTaskInRoom(UUID taskId, UUID roomId) {
        return taskRepository.findByIdAndRoom_Id(taskId, roomId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
    }

    private TaskResponse toResponse(StudyTask t) {
        return TaskResponse.builder()
                .id(t.getId().toString())
                .text(t.getText())
                .isDone(Boolean.TRUE.equals(t.getIsDone()))
                .sortOrder(t.getSortOrder())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getMyTasks(UUID roomId, UUID userId) {
        requireParticipant(roomId, userId);
        return taskRepository.findByUserIdAndRoomIdOrderBySortOrderAsc(userId, roomId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TaskWithLikesResponse> getAllTasksWithLikes(UUID roomId, UUID userId) {
        requireParticipant(roomId, userId);
        List<StudyTask> tasks = taskRepository.findByRoom_IdOrderByUser_IdAscSortOrderAsc(roomId);
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> likeCountByTask = new HashMap<>();
        for (Object[] row : likeRepository.countByTaskGroupedInRoom(roomId)) {
            likeCountByTask.put((UUID) row[0], (Long) row[1]);
        }
        List<UUID> taskIds = tasks.stream().map(StudyTask::getId).toList();
        Set<UUID> likedByMe = taskIds.isEmpty()
                ? Set.of()
                : likeRepository.findTaskIdsLikedByUserAmong(userId, taskIds);

        return tasks.stream().map(t -> {
            User owner = t.getUser();
            long likes = likeCountByTask.getOrDefault(t.getId(), 0L);
            return TaskWithLikesResponse.builder()
                    .id(t.getId().toString())
                    .text(t.getText())
                    .isDone(Boolean.TRUE.equals(t.getIsDone()))
                    .sortOrder(t.getSortOrder())
                    .ownerId(owner.getId().toString())
                    .ownerName(owner.getName())
                    .isBot(owner.getEmail() != null && owner.getEmail().equalsIgnoreCase("motivbot@syncroom.local"))
                    .likeCount(likes)
                    .likedByMe(likedByMe.contains(t.getId()))
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> getLeaderboard(UUID roomId, UUID userId) {
        requireParticipant(roomId, userId);
        var room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        if (LEADERBOARD_FORBIDDEN_CONTEXT.equals(room.getContext())) {
            throw new BadRequestException("Leaderboard is not available in leisure rooms");
        }
        List<RoomParticipant> participants = participantRepository.findByRoomId(roomId);
        List<LeaderboardEntryResponse> rows = new ArrayList<>();
        for (RoomParticipant p : participants) {
            User u = p.getUser();
            long totalLikes = likeRepository.countLikesOnTasksOwnedByUserInRoom(u.getId(), roomId);
            long totalTasks = taskRepository.countByUser_IdAndRoom_Id(u.getId(), roomId);
            long completedTasks = taskRepository.countByUser_IdAndRoom_IdAndIsDoneTrue(u.getId(), roomId);
            rows.add(LeaderboardEntryResponse.builder()
                    .userId(u.getId().toString())
                    .userName(u.getName())
                    .avatarUrl(u.getAvatarUrl())
                    .totalLikes(totalLikes)
                    .completedTasks(completedTasks)
                    .totalTasks(totalTasks)
                    .build());
        }
        rows.sort(Comparator.comparingLong(LeaderboardEntryResponse::getTotalLikes).reversed()
                .thenComparing(LeaderboardEntryResponse::getUserName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return rows;
    }

    @Transactional(readOnly = true)
    public LeaderboardEntryResponse getMyLeaderboardEntry(UUID roomId, UUID userId) {
        requireParticipant(roomId, userId);
        var room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        if (LEADERBOARD_FORBIDDEN_CONTEXT.equals(room.getContext())) {
            throw new BadRequestException("Leaderboard is not available in leisure rooms");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        long totalLikes = likeRepository.countLikesOnTasksOwnedByUserInRoom(userId, roomId);
        long totalTasks = taskRepository.countByUser_IdAndRoom_Id(userId, roomId);
        long completedTasks = taskRepository.countByUser_IdAndRoom_IdAndIsDoneTrue(userId, roomId);

        return LeaderboardEntryResponse.builder()
                .userId(user.getId().toString())
                .userName(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .totalLikes(totalLikes)
                .completedTasks(completedTasks)
                .totalTasks(totalTasks)
                .build();
    }

    @Transactional
    public TaskLikeMutationResponse likeTask(UUID roomId, UUID userId, UUID taskId) {
        requireParticipant(roomId, userId);
        StudyTask task = requireTaskInRoom(taskId, roomId);
        if (task.getUser().getId().equals(userId)) {
            throw new BadRequestException("Cannot like your own task");
        }
        if (likeRepository.existsByTask_IdAndUser_Id(taskId, userId)) {
            return TaskLikeMutationResponse.builder()
                    .taskId(taskId.toString())
                    .likeCount(likeRepository.countByTask_Id(taskId))
                    .likedByMe(true)
                    .build();
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        likeRepository.save(TaskLike.builder().task(task).user(user).build());
        long count = likeRepository.countByTask_Id(taskId);
        publishTaskEvent(roomId, StudyTaskWsEventType.TASK_LIKED, Map.of(
                "taskId", taskId.toString(),
                "userId", userId.toString(),
                "userName", user.getName(),
                "likeCount", count,
                "action", "LIKE"
        ));
        return TaskLikeMutationResponse.builder()
                .taskId(taskId.toString())
                .likeCount(count)
                .likedByMe(true)
                .build();
    }

    @Transactional
    public TaskLikeMutationResponse unlikeTask(UUID roomId, UUID userId, UUID taskId) {
        requireParticipant(roomId, userId);
        requireTaskInRoom(taskId, roomId);
        long countBefore = likeRepository.countByTask_Id(taskId);
        if (!likeRepository.existsByTask_IdAndUser_Id(taskId, userId)) {
            return TaskLikeMutationResponse.builder()
                    .taskId(taskId.toString())
                    .likeCount(countBefore)
                    .likedByMe(false)
                    .build();
        }
        likeRepository.deleteByTask_IdAndUser_Id(taskId, userId);
        long count = likeRepository.countByTask_Id(taskId);
        publishTaskEvent(roomId, StudyTaskWsEventType.TASK_UNLIKED, Map.of(
                "taskId", taskId.toString(),
                "userId", userId.toString(),
                "likeCount", count,
                "action", "UNLIKE"
        ));
        return TaskLikeMutationResponse.builder()
                .taskId(taskId.toString())
                .likeCount(count)
                .likedByMe(false)
                .build();
    }

    @Transactional
    public TaskResponse create(UUID roomId, UUID userId, CreateTaskRequest request) {
        var room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (!participantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BadRequestException("User is not a participant of this room");
        }

        int nextOrder = taskRepository.findByUserIdAndRoomIdOrderBySortOrderAsc(userId, roomId)
                .stream()
                .map(StudyTask::getSortOrder)
                .max(Integer::compareTo)
                .orElse(-1) + 1;

        StudyTask task = StudyTask.builder()
                .user(user)
                .room(room)
                .text(request.getText())
                .isDone(false)
                .sortOrder(nextOrder)
                .build();

        StudyTask saved = taskRepository.save(task);
        publishTaskEvent(roomId, StudyTaskWsEventType.TASK_CREATED, Map.of(
                "taskId", saved.getId().toString(),
                "text", saved.getText(),
                "isDone", Boolean.TRUE.equals(saved.getIsDone()),
                "sortOrder", saved.getSortOrder(),
                "ownerId", user.getId().toString(),
                "ownerName", user.getName(),
                "likeCount", 0L,
                "likedByMe", false
        ));
        return toResponse(saved);
    }

    @Transactional
    public TaskResponse update(UUID roomId, UUID userId, UUID taskId, UpdateTaskRequest request) {
        StudyTask task = taskRepository.findByIdAndUserIdAndRoomId(taskId, userId, roomId)
                .orElseThrow(() -> new NotFoundException("Task not found"));

        if (request.getText() != null) {
            task.setText(request.getText());
        }
        if (request.getIsDone() != null) {
            task.setIsDone(request.getIsDone());
        }
        if (request.getSortOrder() != null) {
            task.setSortOrder(request.getSortOrder());
        }

        StudyTask updated = taskRepository.save(task);
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", updated.getId().toString());
        payload.put("text", updated.getText());
        payload.put("isDone", Boolean.TRUE.equals(updated.getIsDone()));
        payload.put("sortOrder", updated.getSortOrder());
        payload.put("ownerId", updated.getUser().getId().toString());
        payload.put("ownerName", updated.getUser().getName());
        publishTaskEvent(roomId, StudyTaskWsEventType.TASK_UPDATED, payload);
        return toResponse(updated);
    }

    @Transactional
    public void delete(UUID roomId, UUID userId, UUID taskId) {
        StudyTask task = taskRepository.findByIdAndUserIdAndRoomId(taskId, userId, roomId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        UUID ownerId = task.getUser().getId();
        taskRepository.delete(task);
        publishTaskEvent(roomId, StudyTaskWsEventType.TASK_DELETED, Map.of(
                "taskId", taskId.toString(),
                "ownerId", ownerId.toString()
        ));
    }
}
