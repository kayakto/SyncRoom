package ru.syncroom.auth.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import ru.syncroom.auth.client.vk.VkApiResponse;
import ru.syncroom.auth.client.yandex.YandexApiResponse;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.UnauthorizedException;
import ru.syncroom.users.domain.AuthProvider;

/**
 * Client for external OAuth providers (VK, Yandex).
 * 
 * Реализует реальные API вызовы к внешним OAuth провайдерам.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalOAuthClient {

    private final RestTemplate restTemplate;
    
    private static final String VK_API_VERSION = "5.131";
    private static final String VK_API_URL = "https://api.vk.com/method/users.get";
    private static final String YANDEX_API_URL = "https://login.yandex.ru/info";

    /**
     * Fetches user profile from external OAuth provider.
     * 
     * @param provider OAuth provider (VK or YANDEX)
     * @param accessToken Access token from the provider
     * @return User profile information
     * @throws UnauthorizedException if token is invalid
     * @throws BadRequestException if provider is not supported or API error
     */
    public OAuthUserProfile getUserProfile(AuthProvider provider, String accessToken) {
        log.debug("Fetching user profile from {} with token", provider);
        
        return switch (provider) {
            case VK -> fetchVkUserProfile(accessToken);
            case YANDEX -> fetchYandexUserProfile(accessToken);
            default -> throw new BadRequestException("Unsupported OAuth provider: " + provider);
        };
    }

    /**
     * Fetches user profile from VK API.
     * 
     * @param accessToken VK access token
     * @return User profile
     */
    private OAuthUserProfile fetchVkUserProfile(String accessToken) {
        try {
            String url = String.format(
                    "%s?access_token=%s&v=%s&fields=photo_200,photo_max_orig,email",
                    VK_API_URL,
                    accessToken,
                    VK_API_VERSION
            );
            
            log.debug("Calling VK API: {}", url.replace(accessToken, "***"));
            
            ResponseEntity<VkApiResponse> response = restTemplate.getForEntity(url, VkApiResponse.class);
            VkApiResponse vkResponse = response.getBody();
            
            if (vkResponse == null) {
                throw new UnauthorizedException("Empty response from VK API");
            }
            
            // Проверка на ошибку от VK API
            if (vkResponse.getError() != null) {
                VkApiResponse.VkError error = vkResponse.getError();
                log.warn("VK API error: {} - {}", error.getErrorCode(), error.getErrorMsg());
                
                // Коды ошибок VK: 5 - invalid token, 113 - invalid user id, и т.д.
                if (error.getErrorCode() == 5) {
                    throw new UnauthorizedException("Invalid VK access token");
                }
                throw new BadRequestException("VK API error: " + error.getErrorMsg());
            }
            
            if (vkResponse.getResponse() == null || vkResponse.getResponse().isEmpty()) {
                throw new UnauthorizedException("No user data in VK API response");
            }
            
            VkApiResponse.VkUser vkUser = vkResponse.getResponse().get(0);
            
            if (vkUser.getId() == null) {
                throw new UnauthorizedException("Invalid VK user data");
            }
            
            return OAuthUserProfile.builder()
                    .id(String.valueOf(vkUser.getId()))
                    .name(vkUser.getFullName())
                    .email(vkUser.getEmail())
                    .avatarUrl(vkUser.getAvatarUrl())
                    .build();
                    
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("VK API unauthorized: {}", e.getMessage());
            throw new UnauthorizedException("Invalid VK access token");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("VK API error: {}", e.getMessage());
            throw new BadRequestException("VK API error: " + e.getMessage());
        } catch (RestClientException e) {
            log.error("Failed to call VK API: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to connect to VK API: " + e.getMessage());
        }
    }

    /**
     * Fetches user profile from Yandex API.
     * 
     * @param accessToken Yandex access token
     * @return User profile
     */
    private OAuthUserProfile fetchYandexUserProfile(String accessToken) {
        try {
            String url = String.format(
                    "%s?format=json&oauth_token=%s",
                    YANDEX_API_URL,
                    accessToken
            );
            
            log.debug("Calling Yandex API: {}", url.replace(accessToken, "***"));
            
            ResponseEntity<YandexApiResponse> response = restTemplate.getForEntity(url, YandexApiResponse.class);
            YandexApiResponse yandexResponse = response.getBody();
            
            if (yandexResponse == null) {
                throw new UnauthorizedException("Empty response from Yandex API");
            }
            
            if (yandexResponse.getId() == null || yandexResponse.getId().isEmpty()) {
                throw new UnauthorizedException("Invalid Yandex user data: missing ID");
            }
            
            return OAuthUserProfile.builder()
                    .id(yandexResponse.getId())
                    .name(yandexResponse.getFullName())
                    .email(yandexResponse.getEmail())
                    .avatarUrl(yandexResponse.getAvatarUrl())
                    .build();
                    
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Yandex API unauthorized: {}", e.getMessage());
            throw new UnauthorizedException("Invalid Yandex access token");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Yandex API error: {}", e.getMessage());
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new UnauthorizedException("Invalid Yandex access token");
            }
            throw new BadRequestException("Yandex API error: " + e.getMessage());
        } catch (RestClientException e) {
            log.error("Failed to call Yandex API: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to connect to Yandex API: " + e.getMessage());
        }
    }

    /**
     * DTO for external OAuth user profile.
     */
    public static class OAuthUserProfile {
        private String id;
        private String name;
        private String email;
        private String avatarUrl;

        public static OAuthUserProfileBuilder builder() {
            return new OAuthUserProfileBuilder();
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public static class OAuthUserProfileBuilder {
            private String id;
            private String name;
            private String email;
            private String avatarUrl;

            public OAuthUserProfileBuilder id(String id) {
                this.id = id;
                return this;
            }

            public OAuthUserProfileBuilder name(String name) {
                this.name = name;
                return this;
            }

            public OAuthUserProfileBuilder email(String email) {
                this.email = email;
                return this;
            }

            public OAuthUserProfileBuilder avatarUrl(String avatarUrl) {
                this.avatarUrl = avatarUrl;
                return this;
            }

            public OAuthUserProfile build() {
                OAuthUserProfile profile = new OAuthUserProfile();
                profile.setId(id);
                profile.setName(name);
                profile.setEmail(email);
                profile.setAvatarUrl(avatarUrl);
                return profile;
            }
        }
    }
}
