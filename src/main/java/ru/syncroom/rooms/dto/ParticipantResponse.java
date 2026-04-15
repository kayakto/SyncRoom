package ru.syncroom.rooms.dto;

import lombok.Builder;
import lombok.Data;
import ru.syncroom.rooms.domain.ParticipantRole;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.users.domain.User;

import java.time.OffsetDateTime;

/**
 * DTO representing a single room participant.
 * Returned inside JoinRoomResponse and WebSocket events.
 */
@Data
@Builder
public class ParticipantResponse {

    private String userId;
    private String name;
    private String avatarUrl;
    private ParticipantRole role;
    private OffsetDateTime joinedAt;

    public static ParticipantResponse from(RoomParticipant participant) {
        User user = participant.getUser();
        return ParticipantResponse.builder()
                .userId(user.getId().toString())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .role(participant.getRole())
                .joinedAt(participant.getJoinedAt())
                .build();
    }
}
