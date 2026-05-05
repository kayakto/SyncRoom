package ru.syncroom.games.service;

import java.util.Optional;

/**
 * Результат выдачи Gartic PNG: редирект на CDN или тело из локального/API-хранилища.
 */
public record GarticDrawingServeResult(Optional<String> redirectUrl, byte[] body) {

    public static GarticDrawingServeResult redirect(String url) {
        return new GarticDrawingServeResult(Optional.of(url), null);
    }

    public static GarticDrawingServeResult bytes(byte[] png) {
        return new GarticDrawingServeResult(Optional.empty(), png);
    }
}
