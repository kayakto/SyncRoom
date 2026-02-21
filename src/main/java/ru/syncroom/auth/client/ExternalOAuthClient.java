package ru.syncroom.auth.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.syncroom.users.domain.AuthProvider;

/**
 * Client for external OAuth providers (VK, Yandex).
 * 
 * Currently implements a stub that returns fake user data.
 * In production, this should be replaced with actual API calls to VK/Yandex OAuth endpoints.
 */
@Slf4j
@Service
public class ExternalOAuthClient {

    /**
     * Fetches user profile from external OAuth provider.
     * 
     * @param provider OAuth provider (VK or YANDEX)
     * @param accessToken Access token from the provider
     * @return User profile information
     */
    public OAuthUserProfile getUserProfile(AuthProvider provider, String accessToken) {
        log.debug("Fetching user profile from {} (stub implementation)", provider);
        
        // TODO: Replace with actual API calls
        // For VK: https://api.vk.com/method/users.get?access_token={token}&v=5.131
        // For Yandex: https://login.yandex.ru/info?format=json&oauth_token={token}
        
        // Stub implementation - returns fake data
        return OAuthUserProfile.builder()
                .id("external_" + provider.getValue() + "_" + accessToken.hashCode())
                .name("User from " + provider.getValue())
                .email(provider.getValue() + "_user@example.com")
                .avatarUrl("https://example.com/avatar/" + provider.getValue() + ".jpg")
                .build();
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
