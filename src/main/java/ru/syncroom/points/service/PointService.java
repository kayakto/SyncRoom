package ru.syncroom.points.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.points.domain.Point;
import ru.syncroom.points.dto.CreatePointRequest;
import ru.syncroom.points.dto.PointResponse;
import ru.syncroom.points.repository.PointRepository;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PointService {

    private final PointRepository pointRepository;
    private final UserRepository userRepository;

    public List<PointResponse> getPoints(UUID userId) {
        return pointRepository.findByUserId(userId).stream()
                .map(PointResponse::from)
                .toList();
    }

    @Transactional
    public PointResponse createPoint(UUID userId, CreatePointRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        validateContext(request.getContext());

        Point point = Point.builder()
                .user(user)
                .context(request.getContext().toLowerCase())
                .title(request.getTitle())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();

        return PointResponse.from(pointRepository.save(point));
    }

    @Transactional
    public PointResponse updatePoint(UUID userId, UUID pointId, CreatePointRequest request) {
        Point point = pointRepository.findByIdAndUserId(pointId, userId)
                .orElseThrow(() -> new NotFoundException("Point not found"));

        validateContext(request.getContext());

        point.setContext(request.getContext().toLowerCase());
        point.setTitle(request.getTitle());
        point.setAddress(request.getAddress());
        point.setLatitude(request.getLatitude());
        point.setLongitude(request.getLongitude());

        return PointResponse.from(pointRepository.save(point));
    }

    @Transactional
    public void deletePoint(UUID userId, UUID pointId) {
        if (pointRepository.findByIdAndUserId(pointId, userId).isEmpty()) {
            throw new NotFoundException("Point not found");
        }
        pointRepository.deleteByIdAndUserId(pointId, userId);
    }

    private void validateContext(String context) {
        Set<String> validContexts = Set.of("work", "study", "sport", "leisure");
        if (!validContexts.contains(context.toLowerCase())) {
            throw new BadRequestException(
                    "Invalid context: " + context + ". Must be one of: " + validContexts);
        }
    }
}
