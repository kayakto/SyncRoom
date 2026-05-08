package ru.syncroom.common.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.syncroom.storage.config.SyncRoomStorageProperties;

/**
 * Turns stored avatar/media paths (e.g. {@code /api/media/avatars/...}) into absolute URLs for clients.
 */
@Component
@RequiredArgsConstructor
public class PublicAbsoluteUrlResolver {

    private final SyncRoomStorageProperties storageProperties;

    /**
     * @return absolute http(s) URL, or {@code null} if input is null/blank
     */
    public String resolve(String stored) {
        if (stored == null) {
            return null;
        }
        String t = stored.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.startsWith("http://") || t.startsWith("https://")) {
            return t;
        }
        String base = storageProperties.normalizedPublicBaseUrl();
        if (base == null || base.isEmpty()) {
            return t.startsWith("/") ? t : "/" + t;
        }
        return base + (t.startsWith("/") ? t : "/" + t);
    }
}
