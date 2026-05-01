package ru.syncroom.rooms.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SeatBotInRoomDto {
    String id;
    String botType;
    String name;
    String avatarUrl;
    String seatId;
}
