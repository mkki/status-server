package com.statoverflow.status.global.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.statoverflow.status.domain.users.dto.BasicUsersDto;
import com.statoverflow.status.domain.users.dto.TierDto;
import com.statoverflow.status.domain.users.service.UsersService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class JwtService {

    private final UsersService usersService;

    @Value("${jwt.secret}")
    private String secret; // application.yml의 secret 값 주입

    // 만료 시간은 application.yml에서 직접 주입받는 것이 더 유연합니다.
    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Access Token 생성
    public String generateAccessToken(BasicUsersDto user) {

        Date now = new Date(System.currentTimeMillis());
        Date validity = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
            .claim("id", user.id())
            .claim("nickname", user.nickname())
            .claim("providerType", user.providerType().name())
            .issuedAt(now)
            .expiration(validity)
            .signWith(secretKey)
            .compact();
    }

    // Refresh Token 생성
    public String generateRefreshToken(BasicUsersDto user) {

        Date now = new Date(System.currentTimeMillis());
        Date validity = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
            .claim("id", user.id())
            .claim("nickname", user.nickname())
            .claim("providerType", user.providerType().name())
            .issuedAt(now)
            .expiration(validity)
            .signWith(secretKey)
            .compact();
    }

    public void deleteCookie(HttpServletResponse response, String name, Boolean isHttpOnly) {
        ResponseCookie cookie1 = ResponseCookie.from(name, "")
            .httpOnly(isHttpOnly)
            .secure(true)
            .path("/")
            .sameSite("None")
            .maxAge(0)
            .build();
        ResponseCookie cookie2 = ResponseCookie.from(name, "")
            .httpOnly(isHttpOnly)
            .secure(true)
            .path("/")
            .maxAge(0)
            .build();
        ResponseCookie cookie3 = ResponseCookie.from(name, "")
            .httpOnly(isHttpOnly)
            .secure(true)
            .path("/")
            .maxAge(0)
            .build();
        ResponseCookie cookie4 = ResponseCookie.from(name, "")
            .httpOnly(isHttpOnly)
            .secure(false)
            .path("/")
            .maxAge(0)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie1.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie2.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie3.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie4.toString());
    }

    // 토큰 유효성 검증 (더 상세한 예외 처리)
    public boolean validateToken(String token) {
        try {
            log.info("Validating token {}", token);
            Jwts.parser().verifyWith(secretKey).build().parseClaimsJws(token);
            log.info("Token validated");
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.info("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }

    public BasicUsersDto parseUsersFromToken(String token) {

        Claims claims = parseToken(token);

        int id = claims.get("id", Integer.class);
        String nickname = claims.get("nickname", String.class);
        String providerType = claims.get("providerType", String.class);


        return BasicUsersDto.of((long) id, nickname, providerType, usersService.getTier((long) id));

    }

    public Long getRemainingTime(String token) {

        Claims claims = parseToken(token);

        try {
            Date expiration = claims.getExpiration();
            Date now = new Date();

            long remainingTimeMillis = expiration.getTime() - now.getTime();

            // 밀리초를 초로 변환
            return TimeUnit.MILLISECONDS.toSeconds(remainingTimeMillis);

        } catch (Exception e) {
            return 0L;
        }
    }

    public Claims parseToken(String token) {

        return Jwts.parser()
            .verifyWith(secretKey) // 또는 setSigningKey()
            .build()
            .parseSignedClaims(token)
            .getPayload();

    }

    public String resolveTokenFromCookie(HttpServletRequest request, String tokenName) {
        if (request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
            .filter(cookie -> tokenName.equals(cookie.getName()))
            .findFirst()
            .map(Cookie::getValue)
            .orElse(null);
    }

    public long getAccessTokenValidityInSeconds() {
        return 60 * 60;
    }

    public long getRefreshTokenValidityInSeconds() {
        return 60 * 60 * 24 * 14;
    }
}