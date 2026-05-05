package ru.syncroom.games.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.storage.config.SyncRoomStorageProperties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class S3GarticDrawingAssetStorage implements GarticDrawingAssetStorage {

    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final S3Client s3Client;
    private final SyncRoomStorageProperties props;

    private String bucket() {
        return props.getS3().getBucket();
    }

    private String prefix() {
        String p = props.getS3().getKeyPrefix();
        return (p == null || p.isBlank()) ? "syncroom" : p.trim().replaceAll("^/+|/+$", "");
    }

    private String objectKey(UUID gameId, UUID assetId) {
        return prefix() + "/gartic/" + gameId + "/" + assetId + ".png";
    }

    @Override
    public boolean exists(UUID gameId, UUID assetId) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket())
                    .key(objectKey(gameId, assetId))
                    .build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            log.warn("S3 headObject: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public UUID savePngBytes(UUID gameId, byte[] pngBytes, int maxBytes) {
        validatePng(pngBytes, maxBytes);
        UUID id = UUID.randomUUID();
        String key = objectKey(gameId, id);
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket())
                            .key(key)
                            .contentType("image/png")
                            .cacheControl("public, max-age=31536000")
                            .build(),
                    RequestBody.fromBytes(pngBytes));
        } catch (S3Exception e) {
            log.warn("S3 putObject {}: {}", key, e.getMessage());
            throw new BadRequestException("Failed to store drawing");
        }
        return id;
    }

    @Override
    public String saveDataUrlAsAssetRef(UUID gameId, String dataUrl, int maxBytes) {
        byte[] png = decodePngBytes(dataUrl, maxBytes);
        UUID id = savePngBytes(gameId, png, maxBytes);
        return ASSET_PREFIX + id;
    }

    @Override
    public byte[] loadPng(UUID gameId, UUID assetId) {
        String key = objectKey(gameId, assetId);
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket())
                    .key(key)
                    .build()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new BadRequestException("Drawing asset not found");
        } catch (S3Exception e) {
            log.warn("S3 getObject {}: {}", key, e.getMessage());
            throw new BadRequestException("Failed to read drawing");
        }
    }

    @Override
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

    @Override
    public String publicImageUrl(UUID gameId, UUID assetId) {
        return props.normalizedMediaPublicBaseUrl() + "/" + objectKey(gameId, assetId);
    }

    @Override
    public boolean redirectPublicDownloads() {
        return true;
    }

    @Override
    public Optional<String> publicDownloadRedirect(UUID gameId, UUID assetId) {
        return Optional.of(publicImageUrl(gameId, assetId));
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
