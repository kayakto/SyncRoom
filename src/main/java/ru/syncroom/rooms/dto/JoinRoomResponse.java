package ru.syncroom.rooms.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response payload for POST /api/rooms/{roomId}/join.
 * Contains the joined room details and the full current participant list.
 */
@Data
@Builder
public class JoinRoomResponse {
    private RoomResponse room;
    private List<ParticipantResponse> participants;
}
