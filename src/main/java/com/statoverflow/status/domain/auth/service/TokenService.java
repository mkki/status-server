package com.statoverflow.status.domain.auth.service;

import java.security.PublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.statoverflow.status.domain.users.dto.BasicUsersDto;
import com.statoverflow.status.global.jwt.JwtService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

	private final JwtService jwtService;

	public void issueAndSetTokens(BasicUsersDto user, HttpServletResponse response) {
		String accessToken = jwtService.generateAccessToken(user);
		String refreshToken = jwtService.generateRefreshToken(user);

		log.info("accessToken : {}", accessToken);
		log.info("refreshToken : {}", refreshToken);

		ResponseCookie accessTokenCookie = setTokenCookie("access_token", accessToken,
			jwtService.getAccessTokenValidityInSeconds(), true);

		ResponseCookie refreshTokenCookie = setTokenCookie("refresh_token", refreshToken,
			jwtService.getRefreshTokenValidityInSeconds(), true);

		ResponseCookie isAuthenticated = setTokenCookie("is_authenticated", "",
			jwtService.getAccessTokenValidityInSeconds(), false);

		log.info("accessTokenCookie : {}", accessTokenCookie);
		log.info("refreshTokenCookie : {}", refreshTokenCookie);

		response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
		response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
		response.addHeader(HttpHeaders.SET_COOKIE, isAuthenticated.toString());
	}


	static ResponseCookie setTokenCookie(String name, String token, Long maxAge, Boolean isHttpOnly) {
		return ResponseCookie.from(name, token)
			.httpOnly(isHttpOnly)
			.secure(true)
			.path("/")
			.domain(".devmkki.cloud")
			.sameSite("Lax")
			.maxAge(Duration.ofSeconds(maxAge))
			.build();
	}
}

