package ru.syncroom.users.service;

import java.util.UUID;

/**
 * Сохранение и выдача файла аватара (локально или S3).
 */
public interface AvatarStorage {

    /**
     * Сохраняет изображение и возвращает публичный URL для поля {@code User.avatarUrl}.
     */
    String saveAndPublicUrl(UUID userId, byte[] imageBytes, String contentType);

    /**
     * Байты для {@code GET /api/media/avatars/{userId}} в режиме local; в режиме S3 не используется.
     */
    byte[] loadOrThrow(UUID userId);

    String detectedExtension(String contentType);
}
