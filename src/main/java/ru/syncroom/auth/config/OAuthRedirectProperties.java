package ru.syncroom.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.auth.oauth")
public class OAuthRedirectProperties {
    private List<String> vkAllowedRedirectUris = new ArrayList<>();
    private List<String> yandexAllowedRedirectUris = new ArrayList<>();
}
