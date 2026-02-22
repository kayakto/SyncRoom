package ru.syncroom.auth.client.vk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DTO for VK API response.
 * Response structure: {"response": [{"id": ..., "first_name": ..., ...}]}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VkApiResponse {
    
    @JsonProperty("response")
    private List<VkUser> response;
    
    @JsonProperty("error")
    private VkError error;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VkUser {
        @JsonProperty("id")
        private Long id;
        
        @JsonProperty("first_name")
        private String firstName;
        
        @JsonProperty("last_name")
        private String lastName;
        
        @JsonProperty("photo_200")
        private String photo200;
        
        @JsonProperty("photo_max_orig")
        private String photoMaxOrig;
        
        @JsonProperty("email")
        private String email;
        
        /**
         * Получить полное имя пользователя.
         */
        public String getFullName() {
            if (firstName == null && lastName == null) {
                return "VK User";
            }
            if (firstName == null) {
                return lastName;
            }
            if (lastName == null) {
                return firstName;
            }
            return firstName + " " + lastName;
        }
        
        /**
         * Получить URL аватара (приоритет photo_200, затем photo_max_orig).
         */
        public String getAvatarUrl() {
            if (photo200 != null && !photo200.isEmpty()) {
                return photo200;
            }
            return photoMaxOrig != null ? photoMaxOrig : null;
        }
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VkError {
        @JsonProperty("error_code")
        private Integer errorCode;
        
        @JsonProperty("error_msg")
        private String errorMsg;
    }
}
