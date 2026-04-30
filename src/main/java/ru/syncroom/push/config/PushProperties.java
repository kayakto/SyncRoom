package ru.syncroom.push.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.push")
public class PushProperties {
    private String vapidPublicKey = "dev-vapid-public-key-placeholder";
}
