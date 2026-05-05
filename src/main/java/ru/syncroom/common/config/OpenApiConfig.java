package ru.syncroom.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) configuration.
 * Provides API documentation at /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Value("${syncroom.storage.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return new OpenAPI()
                .servers(List.of(new Server()
                        .url(base)
                        .description("Публичный URL API (APP_PUBLIC_BASE_URL; для Swagger Try it out по HTTPS)")))
                .info(new Info()
                        .title("SyncRoom API")
                        .version("1.0.0")
                        .description("Backend API для приложения SyncRoom. " +
                                "Поддерживает аутентификацию через OAuth (VK, Yandex) и email/password с использованием JWT токенов."))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT токен для аутентификации. Получите токен через /api/auth/register, /api/auth/email или /api/auth/oauth")));
    }
}
