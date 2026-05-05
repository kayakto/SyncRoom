package ru.syncroom.users.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.storage.config.SyncRoomStorageProperties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@RequiredArgsConstructor
public class S3AvatarStorage implements AvatarStorage {

    private final S3Client s3Client;
    private final SyncRoomStorageProperties props;

    private String bucket() {
        return props.getS3().getBucket();
    }

    private String prefix() {
        String p = props.getS3().getKeyPrefix();
        return (p == null || p.isBlank()) ? "syncroom" : p.trim().replaceAll("^/+|/+$", "");
    }

    private String objectKey(java.util.UUID userId, String ext) {
        return prefix() + "/avatars/" + userId + "." + ext;
    }

    private String contentTypeHeader(String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.split(";")[0].trim();
        }
        return "image/png";
    }

    @Override
    public String saveAndPublicUrl(java.util.UUID userId, byte[] imageBytes, String contentType) {
        validate(contentType, imageBytes);
        String ext = detectedExtension(contentType);
        String key = objectKey(userId, ext);
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket())
                            .key(key)
                            .contentType(contentTypeHeader(contentType))
                            .cacheControl("public, max-age=86400")
                            .build(),
                    RequestBody.fromBytes(imageBytes));
        } catch (S3Exception e) {
            log.warn("S3 avatar put {}: {}", key, e.getMessage());
            throw new BadRequestException("Failed to store avatar");
        }
        return props.normalizedMediaPublicBaseUrl() + "/" + key;
    }

    @Override
    public byte[] loadOrThrow(java.util.UUID userId) {
        throw new NotFoundException("Use avatar URL from profile (CDN)");
    }

    @Override
    public String detectedExtension(String contentType) {
        if (contentType == null) {
            return "png";
        }
        String ct = contentType.toLowerCase().trim();
        if (ct.contains("jpeg") || ct.contains("jpg")) {
            return "jpg";
        }
        if (ct.contains("webp")) {
            return "webp";
        }
        return "png";
    }

    private static void validate(String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Empty file");
        }
        if (bytes.length > 2 * 1024 * 1024) {
            throw new BadRequestException("Avatar too large (max 2 MB)");
        }
        String ct = contentType != null ? contentType.toLowerCase() : "";
        if (!ct.contains("png") && !ct.contains("jpeg") && !ct.contains("jpg") && !ct.contains("webp")) {
            throw new BadRequestException("Avatar must be PNG, JPEG or WebP");
        }
    }
}
