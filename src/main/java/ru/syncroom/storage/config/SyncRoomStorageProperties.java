package ru.syncroom.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Единые настройки файлового хранилища: локально для dev или S3 (Яндекс Object Storage + CDN).
 */
@Data
@ConfigurationProperties(prefix = "syncroom.storage")
public class SyncRoomStorageProperties {

    /**
     * local — файлы на диске приложения; s3 — загрузка в бакет, публичные URL через {@link #publicBaseUrl} (CDN).
     */
    private StorageType type = StorageType.LOCAL;

    /**
     * Публичный origin API за nginx (https://syncroom.ru), без завершающего /. Используется для local: ссылки на /api/...
     */
    private String publicBaseUrl = "http://localhost:8080";

    /**
     * Для режима S3: базовый URL раздачи объектов (CDN), без завершающего /. Если пусто — берётся {@link #publicBaseUrl}.
     */
    private String mediaPublicBaseUrl = "";

    private Local local = new Local();
    private S3 s3 = new S3();

    public enum StorageType {
        LOCAL,
        S3
    }

    @Data
    public static class Local {
        /** Корень для локальных файлов (gartic, аватары). */
        private String dataDir = "./data/storage";
    }

    @Data
    public static class S3 {
        private String endpoint = "https://storage.yandexcloud.net";
        private String region = "ru-central1";
        private String bucket = "";
        private String accessKey = "";
        private String secretKey = "";
        /** Префикс ключей в бакете, например syncroom → syncroom/gartic/... */
        private String keyPrefix = "syncroom";
    }

    public String normalizedPublicBaseUrl() {
        return normalizeUrl(publicBaseUrl);
    }

    /** База для URL объектов в бакете (CDN). */
    public String normalizedMediaPublicBaseUrl() {
        String u = mediaPublicBaseUrl != null && !mediaPublicBaseUrl.isBlank() ? mediaPublicBaseUrl : publicBaseUrl;
        return normalizeUrl(u);
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
