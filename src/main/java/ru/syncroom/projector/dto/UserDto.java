package ru.syncroom.projector.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Compact representation of a user used in projector responses and WS events.
 */
@Data
@Builder
public class UserDto {
    private String id;
    private String name;
    private String avatarUrl;
}
