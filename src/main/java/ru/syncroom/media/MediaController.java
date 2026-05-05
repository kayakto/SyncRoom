package ru.syncroom.media;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.syncroom.users.service.AvatarStorage;

import java.util.UUID;

/**
 * Отдача аватаров с диска приложения (режим {@code syncroom.storage.type=local}).
 * При S3 клиенты используют {@code avatarUrl} из профиля (CDN).
 */
@Tag(name = "Media", description = "Публичная отдача медиа (local storage)")
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "syncroom.storage.type", havingValue = "local", matchIfMissing = true)
public class MediaController {

    private final AvatarStorage avatarStorage;

    @Operation(summary = "Скачать аватар пользователя (local storage)")
    @GetMapping("/avatars/{userId}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable UUID userId) {
        byte[] data = avatarStorage.loadOrThrow(userId);
        return ResponseEntity.ok()
                .contentType(guessType(data))
                .body(data);
    }

    private static MediaType guessType(byte[] data) {
        if (data.length >= 3 && data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) {
            return MediaType.IMAGE_JPEG;
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_PNG;
    }
}
