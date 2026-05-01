package ru.syncroom.rooms.ws;

import lombok.Builder;
import lombok.Data;

/** Payload for SEAT_TAKEN events */
@Data
@Builder
public class SeatTakenPayload {
    private String seatId;
    private OccupantInfo user;
    private int participantCount;
    private int observerCount;

    @Data
    @Builder
    public static class OccupantInfo {
        private String id;
        private String name;
        private String avatarUrl;
        /** true для seat-бота */
        private Boolean isBot;
    }
}
