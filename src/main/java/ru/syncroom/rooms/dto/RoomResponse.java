package ru.syncroom.rooms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import ru.syncroom.rooms.domain.Room;

import java.util.List;

@Data
@Builder
public class RoomResponse {
    private String id;
    private String context;
    private String title;
    private int participantCount;
    private int maxParticipants;
    private String backgroundPicture;
    private List<SeatDto> seats;

    // Use @JsonProperty to ensure Jackson serialises the field as "isActive" (not "active")
    @JsonProperty("isActive")
    private boolean isActive;

    public static RoomResponse from(Room room, int participantCount, List<SeatDto> seats) {
        return RoomResponse.builder()
                .id(room.getId().toString())
                .context(room.getContext())
                .title(room.getTitle())
                .participantCount(participantCount)
                .maxParticipants(room.getMaxParticipants())
                .backgroundPicture(room.getBackgroundPicture())
                .seats(seats)
                .isActive(room.getIsActive())
                .build();
    }

    /** Backward-compat overload (no seats) — used internally where seats are not needed */
    public static RoomResponse from(Room room, int participantCount) {
        return from(room, participantCount, List.of());
    }
}

