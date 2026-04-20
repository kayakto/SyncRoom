package ru.syncroom.games.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.syncroom.common.exception.BadRequestException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

/**
 * Stores Gartic Phone drawing steps as PNG files ({@code asset:<uuid>} in {@link ru.syncroom.games.domain.GarticStep#content}).
 */
@Service
@Slf4j
public class GarticDrawingAssetStorage {

    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    public static final String ASSET_PREFIX = "asset:";

    private final Path baseDir;

    public GarticDrawingAssetStorage(@Value("${games.gartic-assets.dir:./gartic-assets}") String dir) {
        this.baseDir = Path.of(dir).toAbsolutePath().normalize();
    }

    public boolean exists(UUID gameId, UUID assetId) {
        return Files.isRegularFile(filePath(gameId, assetId));
    }

    public UUID savePngBytes(UUID gameId, byte[] pngBytes, int maxBytes) {
        validatePng(pngBytes, maxBytes);
        UUID id = UUID.randomUUID();
        Path path = filePath(gameId, id);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, pngBytes);
        } catch (IOException e) {
            log.warn("Failed to write gartic asset {}: {}", path, e.getMessage());
            throw new BadRequestException("Failed to store drawing");
        }
        return id;
    }

    /**
     * Persists a data URL or raw base64 PNG and returns {@code asset:<uuid>}.
     */
    public String saveDataUrlAsAssetRef(UUID gameId, String dataUrl, int maxBytes) {
        byte[] png = decodePngBytes(dataUrl, maxBytes);
        UUID id = savePngBytes(gameId, png, maxBytes);
        return ASSET_PREFIX + id;
    }

    public byte[] loadPng(UUID gameId, UUID assetId) {
        Path path = filePath(gameId, assetId);
        if (!Files.isRegularFile(path)) {
            throw new BadRequestException("Drawing asset not found");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.warn("Failed to read gartic asset {}: {}", path, e.getMessage());
            throw new BadRequestException("Failed to read drawing");
        }
    }

    /**
     * For inference gateway: returns a data URL, or null if missing / invalid.
     */
    public String toDataUrlForInference(UUID gameId, String storedContent, int maxBytes) {
        if (storedContent == null || storedContent.isBlank()) {
            return null;
        }
        if (storedContent.startsWith(ASSET_PREFIX)) {
            UUID id = UUID.fromString(storedContent.substring(ASSET_PREFIX.length()));
            byte[] png = loadPng(gameId, id);
            if (png.length > maxBytes) {
                return null;
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        }
        if (storedContent.startsWith("data:image/")) {
            return storedContent;
        }
        return null;
    }

    public static boolean isAssetRef(String content) {
        return content != null && content.startsWith(ASSET_PREFIX);
    }

    private Path filePath(UUID gameId, UUID assetId) {
        return baseDir.resolve(gameId.toString()).resolve(assetId + ".png");
    }

    private static void validatePng(byte[] pngBytes, int maxBytes) {
        if (pngBytes == null || pngBytes.length < PNG_MAGIC.length) {
            throw new BadRequestException("Invalid PNG file");
        }
        if (pngBytes.length > maxBytes) {
            throw new BadRequestException("Drawing is too large (max " + (maxBytes / 1024 / 1024) + "MB)");
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (pngBytes[i] != PNG_MAGIC[i]) {
                throw new BadRequestException("File must be PNG");
            }
        }
    }

    private static byte[] decodePngBytes(String dataUrl, int maxBytes) {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw new BadRequestException("Empty drawing");
        }
        String b64;
        if (dataUrl.contains(",")) {
            b64 = dataUrl.substring(dataUrl.indexOf(',') + 1).trim();
        } else {
            b64 = dataUrl.trim();
        }
        byte[] raw = Base64.getDecoder().decode(b64);
        validatePng(raw, maxBytes);
        return raw;
    }
}
