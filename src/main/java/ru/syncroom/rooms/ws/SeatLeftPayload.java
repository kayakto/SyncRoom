package ru.syncroom.rooms.ws;

import lombok.Builder;
import lombok.Data;

/** Payload for SEAT_LEFT events */
@Data
@Builder
public class SeatLeftPayload {
    private String seatId;
    private String userId;
}
