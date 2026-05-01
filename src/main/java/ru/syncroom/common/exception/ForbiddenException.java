package ru.syncroom.common.exception;

/**
 * HTTP 403 — операция запрещена для текущего субъекта (например, не участник комнаты).
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
