package ru.syncroom.projector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.projector.config.SrsProperties;
import ru.syncroom.projector.domain.ProjectorQueueItem;
import ru.syncroom.projector.domain.ProjectorQueueReport;
import ru.syncroom.projector.domain.ProjectorSession;
import ru.syncroom.projector.dto.ProjectorEnqueueResponse;
import ru.syncroom.projector.dto.ProjectorQueueItemResponse;
import ru.syncroom.projector.dto.ProjectorQueueResponse;
import ru.syncroom.projector.dto.ProjectorReportResponse;
import ru.syncroom.projector.dto.ProjectorRequest;
import ru.syncroom.projector.dto.ProjectorResponse;
import ru.syncroom.projector.dto.UserDto;
import ru.syncroom.projector.repository.ProjectorQueueItemRepository;
import ru.syncroom.projector.repository.ProjectorQueueReportRepository;
import ru.syncroom.projector.repository.ProjectorSessionRepository;
import ru.syncroom.projector.ws.ProjectorEvent;
import ru.syncroom.projector.ws.ProjectorEventType;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;

/**
 * Business logic for the Projector feature.
 *
 * Rules:
 *   1. One projector per room (UNIQUE constraint on room_id).
 *   2. Only the host can send control commands (EMBED) or stop the projector.
 *   3. Any room participant can start / take over the projector.
 *   4. SRS callbacks update is_live; clients cannot set it directly.
 *
 * WS topic: /topic/room/{roomId}/projector
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectorService {

    private static final String PROJECTOR_TOPIC = "/topic/room/%s/projector";
    private static final String PROJECTOR_ALLOWED_CONTEXT = "leisure";
    private static final int MAX_SLOT_SECONDS = 42;
    private static final String WAITING = "WAITING";
    private static final String PLAYING = "PLAYING";
    private static final String DONE = "DONE";

    private final ProjectorSessionRepository projectorRepo;
    private final ProjectorQueueItemRepository queueRepository;
    private final ProjectorQueueReportRepository queueReportRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SrsProperties srsProperties;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> roomTimeouts = new ConcurrentHashMap<>();
    @Value("${app.projector.report-threshold:2}")
    private int reportThreshold;

    // ─── Helpers ────────────────────────────────────────────────────────────

    private String projectorTopic(UUID roomId) {
        return String.format(PROJECTOR_TOPIC, roomId);
    }

    private void publish(UUID roomId, ProjectorEventType type, Object payload) {
        messagingTemplate.convertAndSend(projectorTopic(roomId), ProjectorEvent.of(type, payload));
    }

    private void publishQueue(UUID roomId) {
        Map<String, Object> payload = new HashMap<>();
        List<ProjectorQueueItemResponse> items = queueRepository
                .findByRoom_IdAndStatusInOrderByCreatedAtAsc(roomId, List.of(PLAYING, WAITING))
                .stream()
                .map(this::toQueueItemResponse)
                .toList();
        payload.put("items", items);
        publish(roomId, ProjectorEventType.PROJECTOR_QUEUE_UPDATED, payload);
    }

    private UserDto toUserDto(User u) {
        return UserDto.builder()
                .id(u.getId().toString())
                .name(u.getName())
                .avatarUrl(u.getAvatarUrl())
                .build();
    }

    private void assertProjectorAllowed(Room room) {
        if (!PROJECTOR_ALLOWED_CONTEXT.equals(room.getContext())) {
            throw new BadRequestException("Projector is available only in leisure rooms");
        }
    }

    private int resolveSlotDuration(ProjectorRequest request) {
        Integer durationSec = request.getDurationSec();
        if (durationSec == null || durationSec <= 0) {
            return MAX_SLOT_SECONDS;
        }
        return Math.min(durationSec, MAX_SLOT_SECONDS);
    }

    private ProjectorQueueItemResponse toQueueItemResponse(ProjectorQueueItem item) {
        return ProjectorQueueItemResponse.builder()
                .queueItemId(item.getId().toString())
                .userId(item.getUser().getId().toString())
                .userName(item.getUser().getName())
                .mode(item.getMode())
                .videoUrl(item.getVideoUrl())
                .videoTitle(item.getVideoTitle())
                .slotDurationSec(item.getSlotDurationSec())
                .status(item.getStatus())
                .build();
    }

    private void cancelRoomTimeout(UUID roomId) {
        ScheduledFuture<?> future = roomTimeouts.remove(roomId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void scheduleRoomTimeout(UUID roomId, int seconds) {
        cancelRoomTimeout(roomId);
        if (seconds <= 0) {
            onSlotTimeout(roomId);
            return;
        }
        ScheduledFuture<?> future = scheduler.schedule(() -> onSlotTimeout(roomId), seconds, TimeUnit.SECONDS);
        roomTimeouts.put(roomId, future);
    }

    private void onSlotTimeout(UUID roomId) {
        try {
            completeCurrentAndPromote(roomId, "TIMEOUT");
        } catch (Exception e) {
            log.warn("Failed to advance projector queue for room {}: {}", roomId, e.getMessage());
        }
    }

    private void startSessionFromQueueItem(ProjectorQueueItem item) {
        UUID roomId = item.getRoom().getId();
        projectorRepo.findByRoomId(roomId).ifPresent(projectorRepo::delete);
        projectorRepo.flush();

        ProjectorSession.ProjectorSessionBuilder sessionBuilder = ProjectorSession.builder()
                .room(item.getRoom())
                .host(item.getUser())
                .mode(item.getMode())
                .videoTitle(item.getVideoTitle())
                .isPlaying(false)
                .positionMs(0L)
                .isLive(false);

        if ("EMBED".equals(item.getMode())) {
            sessionBuilder.videoUrl(item.getVideoUrl());
        } else {
            String streamKey = "room-" + roomId;
            sessionBuilder.streamKey(streamKey).videoUrl(buildHlsUrl(streamKey));
        }

        ProjectorSession session = projectorRepo.save(sessionBuilder.build());
        publish(roomId, ProjectorEventType.PROJECTOR_STARTED, buildStartedPayload(session));
        scheduleRoomTimeout(roomId, item.getSlotDurationSec());
    }

    private void promoteNext(UUID roomId) {
        var nextOpt = queueRepository.findFirstByRoom_IdAndStatusOrderByCreatedAtAsc(roomId, WAITING);
        if (nextOpt.isEmpty()) {
            cancelRoomTimeout(roomId);
            projectorRepo.findByRoomId(roomId).ifPresent(projectorRepo::delete);
            return;
        }
        var next = nextOpt.get();
        next.setStatus(PLAYING);
        next.setStartedAt(java.time.OffsetDateTime.now());
        queueRepository.save(next);
        startSessionFromQueueItem(next);
    }

    private void completeCurrentAndPromote(UUID roomId, String reason) {
        queueRepository.findFirstByRoom_IdAndStatus(roomId, PLAYING).ifPresent(current -> {
            current.setStatus(DONE);
            current.setFinishedAt(java.time.OffsetDateTime.now());
            queueRepository.save(current);
            publish(roomId, ProjectorEventType.PROJECTOR_STOPPED, Map.of(
                    "hostId", current.getUser().getId().toString(),
                    "reason", reason
            ));
        });
        promoteNext(roomId);
        publishQueue(roomId);
    }

    private ProjectorResponse toResponse(ProjectorSession s, UUID requesterId) {
        boolean isHost = s.getHost().getId().equals(requesterId);

        ProjectorResponse.ProjectorResponseBuilder builder = ProjectorResponse.builder()
                .id(s.getId().toString())
                .roomId(s.getRoom().getId().toString())
                .host(toUserDto(s.getHost()))
                .mode(s.getMode())
                .videoUrl(s.getVideoUrl())
                .videoTitle(s.getVideoTitle())
                .isPlaying(Boolean.TRUE.equals(s.getIsPlaying()))
                .positionMs(s.getPositionMs() != null ? s.getPositionMs() : 0L)
                .isLive(Boolean.TRUE.equals(s.getIsLive()))
                .updatedAt(s.getUpdatedAt().toString());

        if ("STREAM".equals(s.getMode())) {
            builder.streamKey(s.getStreamKey());
            // rtmpUrl is returned only to the host so they can configure OBS
            if (isHost) {
                builder.rtmpUrl(buildRtmpUrl(s.getStreamKey()));
            }
        }

        return builder.build();
    }

    private String buildHlsUrl(String streamKey) {
        return String.format("http://%s:8085/live/%s.m3u8", srsProperties.getHost(), streamKey);
    }

    private String buildRtmpUrl(String streamKey) {
        return String.format("rtmp://%s:1935/live/%s", srsProperties.getHost(), streamKey);
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    /**
     * Get the current projector session for a room.
     * rtmpUrl is only included for the host.
     */
    @Transactional(readOnly = true)
    public ProjectorResponse getProjector(UUID roomId, UUID requesterId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        assertProjectorAllowed(room);
        ProjectorSession session = projectorRepo.findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("Projector is not active in this room"));
        return toResponse(session, requesterId);
    }

    @Transactional(readOnly = true)
    public ProjectorQueueResponse getQueue(UUID roomId, UUID requesterId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        assertProjectorAllowed(room);
        if (!participantRepository.existsByRoomIdAndUserId(roomId, requesterId)) {
            throw new ProjectorForbiddenException("You are not a participant of this room");
        }
        List<ProjectorQueueItemResponse> items = queueRepository
                .findByRoom_IdAndStatusInOrderByCreatedAtAsc(roomId, List.of(PLAYING, WAITING))
                .stream()
                .map(this::toQueueItemResponse)
                .toList();
        return ProjectorQueueResponse.builder().items(items).build();
    }

    /**
     * Start (or take over) the projector in a room.
     * If a session already exists it is overwritten — the caller becomes the new host.
     */
    @Transactional
    public ProjectorEnqueueResponse startProjector(UUID roomId, UUID userId, ProjectorRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        assertProjectorAllowed(room);

        // Only room participants can start the projector
        if (!participantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ProjectorForbiddenException("You are not a participant of this room");
        }

        User host = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String mode = request.getMode().toUpperCase();
        if (!"EMBED".equals(mode) && !"STREAM".equals(mode)) {
            throw new BadRequestException("mode must be 'EMBED' or 'STREAM'");
        }
        if (queueRepository.existsByRoom_IdAndUser_IdAndStatusIn(roomId, userId, Set.of(WAITING, PLAYING))) {
            throw new BadRequestException("User already has an active projector queue item");
        }
        if ("EMBED".equals(mode) && (request.getVideoUrl() == null || request.getVideoUrl().isBlank())) {
            throw new BadRequestException("videoUrl is required for EMBED mode");
        }

        var queueItem = queueRepository.save(ProjectorQueueItem.builder()
                .room(room)
                .user(host)
                .mode(mode)
                .videoUrl(request.getVideoUrl())
                .videoTitle(request.getVideoTitle())
                .requestedDurationSec(request.getDurationSec())
                .slotDurationSec(resolveSlotDuration(request))
                .status(WAITING)
                .build());

        if (queueRepository.findFirstByRoom_IdAndStatus(roomId, PLAYING).isEmpty()) {
            promoteNext(roomId);
        }
        publishQueue(roomId);

        int position = queueRepository.findByRoom_IdAndStatusInOrderByCreatedAtAsc(roomId, List.of(PLAYING, WAITING))
                .stream()
                .map(ProjectorQueueItem::getId)
                .toList()
                .indexOf(queueItem.getId()) + 1;
        ProjectorResponse projector = projectorRepo.findByRoomId(roomId)
                .map(s -> toResponse(s, userId))
                .orElse(null);

        String state = position == 1 ? PLAYING : "QUEUED";
        return ProjectorEnqueueResponse.builder()
                .queueItemId(queueItem.getId().toString())
                .status(state)
                .position(position)
                .slotDurationSec(queueItem.getSlotDurationSec())
                .projector(projector)
                .build();
    }

    /**
     * Stop the projector. Only the current host can do this.
     */
    @Transactional
    public void stopProjector(UUID roomId, UUID userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        assertProjectorAllowed(room);
        ProjectorSession session = projectorRepo.findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("Projector is not active in this room"));

        if (!session.getHost().getId().equals(userId)) {
            throw new ProjectorForbiddenException("Only the projector host can stop it");
        }

        completeCurrentAndPromote(roomId, "HOST_STOPPED");
        log.debug("Projector stopped in room {} by host {}", roomId, userId);
    }

    /**
     * Handle a control command (PLAY / PAUSE / SEEK) from the host (EMBED only).
     * Forwards the command to all room subscribers via WebSocket.
     */
    @Transactional
    public void handleControl(UUID roomId, UUID userId, String action, Long positionMs) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        assertProjectorAllowed(room);
        ProjectorSession session = projectorRepo.findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("Projector is not active in this room"));

        if (!session.getHost().getId().equals(userId)) {
            throw new ProjectorForbiddenException("Only the projector host can send control commands");
        }

        if (!"EMBED".equals(session.getMode())) {
            throw new BadRequestException("Control commands are only available for EMBED mode");
        }

        // Update DB state
        session.setIsPlaying("PLAY".equals(action));
        if (positionMs != null) {
            session.setPositionMs(positionMs);
        }
        projectorRepo.save(session);

        // Broadcast to all subscribers
        publish(roomId, ProjectorEventType.PROJECTOR_CONTROL,
                Map.of("action", action, "positionMs", positionMs != null ? positionMs : 0L));

        log.debug("Projector control in room {}: action={}, positionMs={}", roomId, action, positionMs);
    }

    /**
     * Handle an SRS HTTP callback (on_publish / on_unpublish).
     * Called from the REST endpoint POST /api/projector/srs-callback (no JWT).
     *
     * @param action    "on_publish" or "on_unpublish"
     * @param streamKey the SRS stream name, e.g. "room-{roomId}"
     */
    @Transactional
    public void handleSrsCallback(String action, String streamKey) {
        ProjectorSession session = projectorRepo.findByStreamKey(streamKey)
                .orElse(null);

        if (session == null) {
            log.warn("SRS callback for unknown stream key '{}', action={}", streamKey, action);
            return;
        }

        UUID roomId = session.getRoom().getId();

        if ("on_publish".equals(action)) {
            session.setIsLive(true);
            projectorRepo.save(session);
            publish(roomId, ProjectorEventType.STREAM_LIVE,
                    Map.of("videoUrl", session.getVideoUrl()));
            log.info("Stream LIVE: roomId={}, streamKey={}", roomId, streamKey);

        } else if ("on_unpublish".equals(action)) {
            session.setIsLive(false);
            projectorRepo.save(session);
            publish(roomId, ProjectorEventType.STREAM_OFFLINE, Map.of());
            log.info("Stream OFFLINE: roomId={}, streamKey={}", roomId, streamKey);
        }
    }

    /**
     * Stop the projector when the host leaves the room.
     * Does nothing if this user is not the host.
     */
    @Transactional
    public void stopIfHost(UUID roomId, UUID userId) {
        projectorRepo.findByRoomId(roomId).ifPresent(session -> {
            if (session.getHost().getId().equals(userId)) {
                completeCurrentAndPromote(roomId, "HOST_LEFT");
                log.debug("Projector auto-stopped: host {} left room {}", userId, roomId);
            }
        });
    }

    @Transactional
    public ProjectorReportResponse reportCurrent(UUID roomId, UUID reporterId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        assertProjectorAllowed(room);
        if (!participantRepository.existsByRoomIdAndUserId(roomId, reporterId)) {
            throw new ProjectorForbiddenException("You are not a participant of this room");
        }

        ProjectorQueueItem current = queueRepository.findFirstByRoom_IdAndStatus(roomId, PLAYING)
                .orElseThrow(() -> new NotFoundException("Projector is not active in this room"));

        if (current.getUser().getId().equals(reporterId)) {
            throw new BadRequestException("Host cannot report own projector slot");
        }

        if (!queueReportRepository.existsByQueueItem_IdAndReporter_Id(current.getId(), reporterId)) {
            User reporter = userRepository.findById(reporterId)
                    .orElseThrow(() -> new NotFoundException("User not found"));
            queueReportRepository.save(ProjectorQueueReport.builder()
                    .queueItem(current)
                    .reporter(reporter)
                    .build());
        }

        int reports = (int) queueReportRepository.countByQueueItem_Id(current.getId());
        boolean removed = reports >= reportThreshold;
        if (removed) {
            Map<String, Object> payload = Map.of(
                    "queueItemId", current.getId().toString(),
                    "hostId", current.getUser().getId().toString(),
                    "reportsCount", reports,
                    "threshold", reportThreshold
            );
            publish(roomId, ProjectorEventType.REMOVED_BY_REPORTS, payload);
            messagingTemplate.convertAndSendToUser(
                    current.getUser().getId().toString(),
                    "/queue/projector",
                    ProjectorEvent.of(ProjectorEventType.REMOVED_BY_REPORTS, payload)
            );
            completeCurrentAndPromote(roomId, "REMOVED_BY_REPORTS");
        }

        return ProjectorReportResponse.builder()
                .queueItemId(current.getId().toString())
                .reportsCount(reports)
                .threshold(reportThreshold)
                .removed(removed)
                .build();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Map<String, Object> buildStartedPayload(ProjectorSession s) {
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("host", toUserDto(s.getHost()));
        payload.put("mode", s.getMode());
        payload.put("videoUrl", s.getVideoUrl());
        payload.put("videoTitle", s.getVideoTitle());
        if ("STREAM".equals(s.getMode())) {
            payload.put("streamKey", s.getStreamKey());
        }
        return payload;
    }

    // ─── Custom exceptions ───────────────────────────────────────────────────

    /** 403 Forbidden — not the host or not a participant. */
    public static class ProjectorForbiddenException extends RuntimeException {
        public ProjectorForbiddenException(String msg) { super(msg); }
    }
}
