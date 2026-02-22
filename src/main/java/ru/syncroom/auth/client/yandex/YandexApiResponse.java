package ru.syncroom.auth.client.yandex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO for Yandex OAuth API response.
 * Response structure from https://login.yandex.ru/info?format=json&oauth_token={token}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class YandexApiResponse {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("login")
    private String login;
    
    @JsonProperty("first_name")
    private String firstName;
    
    @JsonProperty("last_name")
    private String lastName;
    
    @JsonProperty("display_name")
    private String displayName;
    
    @JsonProperty("real_name")
    private String realName;
    
    @JsonProperty("default_email")
    private String defaultEmail;
    
    @JsonProperty("emails")
    private List<String> emails;
    
    @JsonProperty("default_avatar_id")
    private String defaultAvatarId;
    
    @JsonProperty("is_avatar_empty")
    private Boolean isAvatarEmpty;
    
    @JsonProperty("avatar_url")
    private String avatarUrl;
    
    /**
     * Получить полное имя пользователя.
     * Приоритет: real_name > display_name > first_name + last_name > login
     */
    public String getFullName() {
        if (realName != null && !realName.isEmpty()) {
            return realName;
        }
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        if (firstName != null || lastName != null) {
            String name = "";
            if (firstName != null) {
                name = firstName;
            }
            if (lastName != null) {
                name = name.isEmpty() ? lastName : name + " " + lastName;
            }
            if (!name.isEmpty()) {
                return name;
            }
        }
        return login != null ? login : "Yandex User";
    }
    
    /**
     * Получить email пользователя.
     * Приоритет: default_email > первый из emails
     */
    public String getEmail() {
        if (defaultEmail != null && !defaultEmail.isEmpty()) {
            return defaultEmail;
        }
        if (emails != null && !emails.isEmpty()) {
            return emails.get(0);
        }
        return null;
    }
    
    /**
     * Получить URL аватара.
     */
    public String getAvatarUrl() {
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            return avatarUrl;
        }
        if (defaultAvatarId != null && !defaultAvatarId.isEmpty() && !Boolean.TRUE.equals(isAvatarEmpty)) {
            // Формируем URL аватара по ID
            return "https://avatars.yandex.net/get-yapic/" + defaultAvatarId + "/islands-200";
        }
        return null;
    }
}
