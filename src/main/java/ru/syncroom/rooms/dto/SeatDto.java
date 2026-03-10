package ru.syncroom.rooms.dto;

import lombok.Builder;
import lombok.Data;
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

        public static OccupantDto from(User user) {
            return OccupantDto.builder()
                    .id(user.getId().toString())
                    .name(user.getName())
                    .avatarUrl(user.getAvatarUrl())
                    .build();
        }
    }

    public static SeatDto from(Seat seat) {
        return SeatDto.builder()
                .id(seat.getId().toString())
                .x(seat.getX())
                .y(seat.getY())
                .occupiedBy(seat.getOccupiedBy() != null
                        ? OccupantDto.from(seat.getOccupiedBy())
                        : null)
                .build();
    }
}
