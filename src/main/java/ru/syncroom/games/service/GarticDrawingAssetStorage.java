package ru.syncroom.games.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Хранилище PNG-ассетов Gartic Phone ({@code asset:<uuid>} в {@link ru.syncroom.games.domain.GarticStep#content}).
 */
public interface GarticDrawingAssetStorage {

    String ASSET_PREFIX = "asset:";

    static boolean isAssetRef(String content) {
        return content != null && content.startsWith(ASSET_PREFIX);
    }

    boolean exists(UUID gameId, UUID assetId);

    UUID savePngBytes(UUID gameId, byte[] pngBytes, int maxBytes);

    byte[] loadPng(UUID gameId, UUID assetId);

    String saveDataUrlAsAssetRef(UUID gameId, String dataUrl, int maxBytes);

    String toDataUrlForInference(UUID gameId, String storedContent, int maxBytes);

    /**
     * Абсолютный или относительный URL для клиента (CDN при S3, API при local).
     */
    String publicImageUrl(UUID gameId, UUID assetId);

    /**
     * Если true — после проверки прав {@code GET .../gartic/drawings/{id}} отдаёт 302 на {@link #publicImageUrl}.
     */
    default boolean redirectPublicDownloads() {
        return false;
    }

    /**
     * Для режима публичного объекта в бакете — редирект; иначе пусто и читаем байты через {@link #loadPng}.
     */
    default Optional<String> publicDownloadRedirect(UUID gameId, UUID assetId) {
        return Optional.empty();
    }
}
