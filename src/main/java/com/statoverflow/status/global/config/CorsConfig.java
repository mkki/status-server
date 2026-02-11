package com.statoverflow.status.global.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();

		configuration.setAllowedOriginPatterns(
			List.of("http://localhost:5500",
				"http://localhost:5173",
				"https://app.devmkki.cloud",
				"http://localhost:8080",
				"https://appleid.apple.com"));
		// 허용 메서드 지정
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		// 클라이언트 요청 허용 헤더
		configuration.setAllowedHeaders(List.of("*"));
		// preflight 요청 결과 캐시
		configuration.setMaxAge(3600L);
		// 인증이 필요한 요청 허용
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
