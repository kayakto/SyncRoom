package ru.syncroom.users.service;

import lombok.extern.slf4j.Slf4j;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class LocalAvatarStorage implements AvatarStorage {

    private final Path baseDir;
    private final String publicBaseUrl;

    public LocalAvatarStorage(String dir, String publicBaseUrl) {
        this.baseDir = Path.of(dir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl;
    }

    @Override
    public String saveAndPublicUrl(java.util.UUID userId, byte[] imageBytes, String contentType) {
        validate(contentType, imageBytes);
        String ext = detectedExtension(contentType);
        Path path = baseDir.resolve(userId + "." + ext);
        try {
            Files.createDirectories(baseDir);
            Files.write(path, imageBytes);
        } catch (IOException e) {
            log.warn("Avatar write {}: {}", path, e.getMessage());
            throw new BadRequestException("Failed to store avatar");
        }
        String suffix = "/api/media/avatars/" + userId;
        if (publicBaseUrl.isBlank()) {
            return suffix;
        }
        return publicBaseUrl + suffix;
    }

    @Override
    public byte[] loadOrThrow(java.util.UUID userId) {
        for (String ext : new String[]{"jpg", "jpeg", "png", "webp"}) {
            Path p = baseDir.resolve(userId + "." + ext);
            if (Files.isRegularFile(p)) {
                try {
                    return Files.readAllBytes(p);
                } catch (IOException e) {
                    log.warn("Avatar read {}: {}", p, e.getMessage());
                    throw new BadRequestException("Failed to read avatar");
                }
            }
        }
        throw new NotFoundException("Avatar not found");
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
