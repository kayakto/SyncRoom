package ru.syncroom.rooms.dto;

import lombok.Builder;
import lombok.Data;
import ru.syncroom.rooms.domain.RoomSeatBot;
import ru.syncroom.rooms.domain.Seat;
import ru.syncroom.users.domain.User;

/**
 * DTO for a seat in a room.
 *
 * occupiedBy is either null (free) or a minimal UserDto (id, name, avatarUrl).
 */
@Data
@Builder
public class SeatDto {

    private String id;
    private double x;
    private double y;
    private OccupantDto occupiedBy;

    /**
     * Minimal user info surfaced inside SeatDto.
     * Keeps the response small — we only need id/name/avatarUrl.
     */
    @Data
    @Builder
    public static class OccupantDto {
        private String id;
        private String name;
        private String avatarUrl;
        private Boolean isBot;

        public static OccupantDto fromUser(User user) {
            return OccupantDto.builder()
                    .id(user.getId().toString())
                    .name(user.getName())
                    .avatarUrl(user.getAvatarUrl())
                    .isBot(false)
                    .build();
        }

        public static OccupantDto fromSeatBot(RoomSeatBot bot) {
            return OccupantDto.builder()
                    .id(bot.getId().toString())
                    .name(bot.getName())
                    .avatarUrl(bot.getAvatarUrl())
                    .isBot(true)
                    .build();
        }
    }

    public static SeatDto from(Seat seat) {
        OccupantDto occupant = null;
        if (seat.getOccupiedBy() != null) {
            occupant = OccupantDto.fromUser(seat.getOccupiedBy());
        } else if (seat.getSeatBot() != null) {
            occupant = OccupantDto.fromSeatBot(seat.getSeatBot());
        }
        return SeatDto.builder()
                .id(seat.getId().toString())
                .x(seat.getX())
                .y(seat.getY())
                .occupiedBy(occupant)
                .build();
    }
}
