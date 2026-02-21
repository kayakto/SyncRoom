package ru.syncroom.users.domain;

/**
 * Authentication provider types.
 * VK and YANDEX are OAuth providers, EMAIL is for email/password authentication.
 */
public enum AuthProvider {
    VK("vk"),
    YANDEX("yandex"),
    EMAIL("email");

    private final String value;

    AuthProvider(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AuthProvider fromString(String value) {
        if (value == null) {
            return null;
        }
        for (AuthProvider provider : AuthProvider.values()) {
            if (provider.value.equalsIgnoreCase(value)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown provider: " + value);
    }
}
