package ru.syncroom.study.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.study.domain.StudyTask;
import ru.syncroom.study.dto.CreateTaskRequest;
import ru.syncroom.study.dto.TaskResponse;
import ru.syncroom.study.dto.UpdateTaskRequest;
import ru.syncroom.study.repository.StudyTaskRepository;
import ru.syncroom.users.repository.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyTaskService {

    private final StudyTaskRepository taskRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomParticipantRepository participantRepository;

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
        if (!participantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BadRequestException("User is not a participant of this room");
        }
        return taskRepository.findByUserIdAndRoomIdOrderBySortOrderAsc(userId, roomId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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

        return toResponse(taskRepository.save(task));
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

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(UUID roomId, UUID userId, UUID taskId) {
        StudyTask task = taskRepository.findByIdAndUserIdAndRoomId(taskId, userId, roomId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        taskRepository.delete(task);
    }
}

