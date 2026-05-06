package ru.syncroom.rooms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import ru.syncroom.users.domain.User;

@Value
@Builder
public class TopParticipantResponse {
    String id;

    String name;

    @JsonProperty("avatar_url")
    String avatarUrl;

    public static TopParticipantResponse from(User user) {
        return TopParticipantResponse.builder()
                .id(user.getId().toString())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
