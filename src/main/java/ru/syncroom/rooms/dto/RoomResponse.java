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
    /** Число пользователей за столом (занятые места). */
    private int participantCount;
    /** Участники комнаты без места (наблюдатели в лаунже). */
    private int observerCount;
    private int maxParticipants;
    private String backgroundPicture;
    private List<SeatDto> seats;
    // Keep JSON key spelling requested by frontend contract.
    @JsonProperty("topParicipants")
    private List<TopParticipantResponse> topParticipants;

    // Use @JsonProperty to ensure Jackson serialises the field as "isActive" (not "active")
    @JsonProperty("isActive")
    private boolean isActive;

    public static RoomResponse from(
            Room room,
            int participantCount,
            int observerCount,
            List<SeatDto> seats,
            List<TopParticipantResponse> topParticipants
    ) {
        return RoomResponse.builder()
                .id(room.getId().toString())
                .context(room.getContext())
                .title(room.getTitle())
                .participantCount(participantCount)
                .observerCount(observerCount)
                .maxParticipants(room.getMaxParticipants())
                .backgroundPicture(room.getBackgroundPicture())
                .seats(seats)
                .topParticipants(topParticipants)
                .isActive(room.getIsActive())
                .build();
    }

    public static RoomResponse from(Room room, int participantCount, int observerCount, List<SeatDto> seats) {
        return from(room, participantCount, observerCount, seats, List.of());
    }

    /** Overload without seats */
    public static RoomResponse from(Room room, int participantCount, int observerCount) {
        return from(room, participantCount, observerCount, List.of(), List.of());
    }
}

