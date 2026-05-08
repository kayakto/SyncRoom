package ru.syncroom.rooms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import ru.syncroom.common.web.PublicAbsoluteUrlResolver;
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
        @JsonProperty("isBot")
        private Boolean isBot;

        public static OccupantDto fromUser(User user, PublicAbsoluteUrlResolver urls) {
            return OccupantDto.builder()
                    .id(user.getId().toString())
                    .name(user.getName())
                    .avatarUrl(urls.resolve(user.getAvatarUrl()))
                    .isBot(false)
                    .build();
        }

        public static OccupantDto fromSeatBot(RoomSeatBot bot, PublicAbsoluteUrlResolver urls) {
            return OccupantDto.builder()
                    .id(bot.getId().toString())
                    .name(bot.getName())
                    .avatarUrl(urls.resolve(bot.getAvatarUrl()))
                    .isBot(true)
                    .build();
        }
    }

    public static SeatDto from(Seat seat, PublicAbsoluteUrlResolver urls) {
        OccupantDto occupant = null;
        if (seat.getOccupiedBy() != null) {
            occupant = OccupantDto.fromUser(seat.getOccupiedBy(), urls);
        } else if (seat.getSeatBot() != null) {
            occupant = OccupantDto.fromSeatBot(seat.getSeatBot(), urls);
        }
        return SeatDto.builder()
                .id(seat.getId().toString())
                .x(seat.getX())
                .y(seat.getY())
                .occupiedBy(occupant)
                .build();
    }
}
