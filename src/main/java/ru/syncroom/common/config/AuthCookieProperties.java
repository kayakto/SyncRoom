package ru.syncroom.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.auth.cookies")
public class AuthCookieProperties {
    private String accessTokenName = "SR_ACCESS_TOKEN";
    private String refreshTokenName = "SR_REFRESH_TOKEN";
    private String domain;
    private String path = "/";
    private boolean secure = false;
    private String sameSite = "Lax";
}
