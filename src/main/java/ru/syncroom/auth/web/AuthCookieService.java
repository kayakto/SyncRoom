package ru.syncroom.auth.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import ru.syncroom.common.config.AuthCookieProperties;
import ru.syncroom.common.config.JwtProperties;

@Service
@RequiredArgsConstructor
public class AuthCookieService {

    private final AuthCookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public void writeAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildAccessCookie(accessToken).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken).toString());
    }

    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildExpired(cookieProperties.getAccessTokenName()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildExpired(cookieProperties.getRefreshTokenName()).toString());
    }

    private ResponseCookie buildAccessCookie(String token) {
        return base(cookieProperties.getAccessTokenName(), token)
                .maxAge(jwtProperties.getAccessTokenExpiration() / 1000)
                .build();
    }

    private ResponseCookie buildRefreshCookie(String token) {
        return base(cookieProperties.getRefreshTokenName(), token)
                .maxAge(jwtProperties.getRefreshTokenExpiration() / 1000)
                .build();
    }

    private ResponseCookie buildExpired(String name) {
        return base(name, "")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .path(cookieProperties.getPath())
                .sameSite(cookieProperties.getSameSite());
        if (cookieProperties.getDomain() != null && !cookieProperties.getDomain().isBlank()) {
            builder.domain(cookieProperties.getDomain());
        }
        return builder;
    }
}
