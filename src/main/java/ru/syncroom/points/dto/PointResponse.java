package ru.syncroom.points.dto;

import lombok.Builder;
import lombok.Data;
import ru.syncroom.points.domain.Point;

@Data
@Builder
public class PointResponse {
    private String id;
    private String userId;
    private String context;
    private String title;
    private String address;
    private Double latitude;
    private Double longitude;

    public static PointResponse from(Point point) {
        return PointResponse.builder()
                .id(point.getId().toString())
                .userId(point.getUser().getId().toString())
                .context(point.getContext())
                .title(point.getTitle())
                .address(point.getAddress())
                .latitude(point.getLatitude())
                .longitude(point.getLongitude())
                .build();
    }
}
