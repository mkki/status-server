package com.statoverflow.status.domain.auth.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper; // ObjectMapper 임포트
import com.statoverflow.status.domain.auth.dto.KakaoTokenResponseDto;
import com.statoverflow.status.domain.auth.dto.KakaoUserInfoDto;
import com.statoverflow.status.domain.auth.dto.OAuthUserInfoDto;
import com.statoverflow.status.global.error.ErrorType;
import com.statoverflow.status.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // MediaType 임포트
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap; // LinkedMultiValueMap 임포트
import org.springframework.util.MultiValueMap; // MultiValueMap 임포트
import org.springframework.web.client.HttpClientErrorException; // HttpClientErrorException 임포트
import org.springframework.web.client.RestTemplate; // RestTemplate 임포트


@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

	private final RestTemplate restTemplate;

	@Value("${spring.security.oauth2.client.registration.kakao.client-id}")
	private String kakaoClientId;

	@Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
	private String kakaoRedirectUri;

	@Value("${spring.security.oauth2.client.provider.kakao.token-uri}")
	private String kakaoTokenUri;

	@Value("${spring.security.oauth2.client.provider.kakao.user-info-uri}")
	private String kakaoUserInfoUri;

	@Value("${spring.security.oauth2.client.registration.kakao.client-secret}")
	private String kakaoClientSecret;

	/**
	 * 카카오 인가 코드를 사용하여 액세스 토큰 및 리프레시 토큰을 카카오로부터 발급받습니다.
	 *
	 * @param code 프론트엔드로부터 받은 카카오 인가 코드
	 * @return 카카오의 토큰 응답
	 */

	public KakaoTokenResponseDto getKakaoTokens(String code) {
		log.info("카카오 토큰 요청 시작, 인가 코드: {}", code);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		// 요청 바디에 포함될 파라미터들
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "authorization_code");
		params.add("client_id", kakaoClientId);
		params.add("redirect_uri", kakaoRedirectUri);
		params.add("client_secret", kakaoClientSecret);
		params.add("code", code);

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
		ParameterizedTypeReference<KakaoTokenResponseDto> responseType = new ParameterizedTypeReference<>() {
		};


		try {

			ResponseEntity<KakaoTokenResponseDto> response = restTemplate.exchange(
				kakaoTokenUri,
				HttpMethod.POST,
				request,
				responseType
			);

			log.info("카카오 액세스 토큰 수신 완료.");
			return response.getBody();

		} catch (Exception e) {
			log.info(e.getMessage());
			throw new CustomException(ErrorType.DEFAULT_ERROR);
		}
	}

	public Long getUserInfo(String accessToken) {
		log.info("유저 정보 요청 시작, 액세스 토큰: {}", accessToken);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.set("Authorization", "Bearer " + accessToken);

		// 요청 바디에 포함될 파라미터들

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(null, headers);
		ParameterizedTypeReference<KakaoUserInfoDto> responseType = new ParameterizedTypeReference<>() {
		};


		try {

			ResponseEntity<KakaoUserInfoDto> response = restTemplate.exchange(
				kakaoUserInfoUri,
				HttpMethod.POST,
				request,
				responseType
			);

			log.info("카카오 유저 provider_id 값 수신 완료, provider_id: {}", response.getBody().getId());
			return response.getBody().getId();

		} catch (Exception e) {
			log.info(e.getMessage());
			throw new CustomException(ErrorType.DEFAULT_ERROR);
		}
	}
}