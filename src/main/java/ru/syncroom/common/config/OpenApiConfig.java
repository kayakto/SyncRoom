package ru.syncroom.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) configuration.
 * Provides API documentation at /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SyncRoom API")
                        .version("1.0.0")
                        .description("Backend API для приложения SyncRoom. " +
                                "Поддерживает аутентификацию через OAuth (VK, Yandex) и email/password с использованием JWT токенов.")
                        .contact(new Contact()
                                .name("SyncRoom Team")
                                .email("support@syncroom.ru"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://syncroom.ru")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT токен для аутентификации. Получите токен через /api/auth/register, /api/auth/email или /api/auth/oauth")));
    }
}
