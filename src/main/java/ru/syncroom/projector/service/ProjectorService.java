package ru.syncroom.projector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.projector.config.SrsProperties;
import ru.syncroom.projector.domain.ProjectorSession;
import ru.syncroom.projector.dto.ProjectorRequest;
import ru.syncroom.projector.dto.ProjectorResponse;
import ru.syncroom.projector.dto.UserDto;
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

    private final ProjectorSessionRepository projectorRepo;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SrsProperties srsProperties;

    // ─── Helpers ────────────────────────────────────────────────────────────

    private String projectorTopic(UUID roomId) {
        return String.format(PROJECTOR_TOPIC, roomId);
    }

    private void publish(UUID roomId, ProjectorEventType type, Object payload) {
        messagingTemplate.convertAndSend(projectorTopic(roomId), ProjectorEvent.of(type, payload));
    }

    private UserDto toUserDto(User u) {
        return UserDto.builder()
                .id(u.getId().toString())
                .name(u.getName())
                .avatarUrl(u.getAvatarUrl())
                .build();
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
        ProjectorSession session = projectorRepo.findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("Projector is not active in this room"));
        return toResponse(session, requesterId);
    }

    /**
     * Start (or take over) the projector in a room.
     * If a session already exists it is overwritten — the caller becomes the new host.
     */
    @Transactional
    public ProjectorResponse startProjector(UUID roomId, UUID userId, ProjectorRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

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

        // Delete any existing session (upsert via delete+save — UNIQUE on room_id)
        projectorRepo.findByRoomId(roomId).ifPresent(projectorRepo::delete);
        projectorRepo.flush();

        ProjectorSession.ProjectorSessionBuilder sessionBuilder = ProjectorSession.builder()
                .room(room)
                .host(host)
                .mode(mode)
                .videoTitle(request.getVideoTitle())
                .isPlaying(false)
                .positionMs(0L)
                .isLive(false);

        if ("EMBED".equals(mode)) {
            if (request.getVideoUrl() == null || request.getVideoUrl().isBlank()) {
                throw new BadRequestException("videoUrl is required for EMBED mode");
            }
            sessionBuilder.videoUrl(request.getVideoUrl());
        } else {
            // STREAM: generate stream key and HLS URL automatically
            String streamKey = "room-" + roomId;
            String hlsUrl = buildHlsUrl(streamKey);
            sessionBuilder.streamKey(streamKey).videoUrl(hlsUrl);
        }

        ProjectorSession saved = projectorRepo.save(sessionBuilder.build());
        log.debug("Projector started in room {} by user {} (mode={})", roomId, userId, mode);

        // Notify all room participants
        publish(roomId, ProjectorEventType.PROJECTOR_STARTED, buildStartedPayload(saved));

        return toResponse(saved, userId);
    }

    /**
     * Stop the projector. Only the current host can do this.
     */
    @Transactional
    public void stopProjector(UUID roomId, UUID userId) {
        ProjectorSession session = projectorRepo.findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("Projector is not active in this room"));

        if (!session.getHost().getId().equals(userId)) {
            throw new ProjectorForbiddenException("Only the projector host can stop it");
        }

        projectorRepo.delete(session);
        log.debug("Projector stopped in room {} by host {}", roomId, userId);

        publish(roomId, ProjectorEventType.PROJECTOR_STOPPED, Map.of("hostId", userId.toString()));
    }

    /**
     * Handle a control command (PLAY / PAUSE / SEEK) from the host (EMBED only).
     * Forwards the command to all room subscribers via WebSocket.
     */
    @Transactional
    public void handleControl(UUID roomId, UUID userId, String action, Long positionMs) {
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
                projectorRepo.delete(session);
                publish(roomId, ProjectorEventType.PROJECTOR_STOPPED, Map.of("hostId", userId.toString()));
                log.debug("Projector auto-stopped: host {} left room {}", userId, roomId);
            }
        });
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
