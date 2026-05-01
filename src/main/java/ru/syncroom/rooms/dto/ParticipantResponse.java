package ru.syncroom.rooms.dto;

import lombok.Builder;
import lombok.Data;
import ru.syncroom.rooms.domain.ParticipantRole;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.domain.RoomSeatBot;
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
    /** Seat-бот: true; человек: false или null (клиент трактует null как false). */
    private Boolean isBot;

    public static ParticipantResponse from(RoomParticipant participant) {
        User user = participant.getUser();
        if (user != null) {
            return ParticipantResponse.builder()
                    .userId(user.getId().toString())
                    .name(user.getName())
                    .avatarUrl(user.getAvatarUrl())
                    .role(participant.getRole())
                    .joinedAt(participant.getJoinedAt())
                    .isBot(false)
                    .build();
        }
        RoomSeatBot bot = participant.getSeatBot();
        if (bot != null) {
            return ParticipantResponse.builder()
                    .userId(bot.getId().toString())
                    .name(bot.getName())
                    .avatarUrl(bot.getAvatarUrl())
                    .role(participant.getRole())
                    .joinedAt(participant.getJoinedAt())
                    .isBot(true)
                    .build();
        }
        throw new IllegalStateException("RoomParticipant must reference either user or seat bot");
    }
}
