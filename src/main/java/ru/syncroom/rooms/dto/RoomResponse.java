package ru.syncroom.rooms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import ru.syncroom.rooms.domain.Room;

@Data
@Builder
public class RoomResponse {
    private String id;
    private String context;
    private String title;
    private int participantCount;
    private int maxParticipants;

    // Use @JsonProperty to ensure Jackson serialises the field as "isActive" (not
    // "active")
    @JsonProperty("isActive")
    private boolean isActive;

    public static RoomResponse from(Room room, int participantCount) {
        return RoomResponse.builder()
                .id(room.getId().toString())
                .context(room.getContext())
                .title(room.getTitle())
                .participantCount(participantCount)
                .maxParticipants(room.getMaxParticipants())
                .isActive(room.getIsActive())
                .build();
    }
}
