package com.statoverflow.status.domain.upload.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.statoverflow.status.domain.upload.dto.UploadResponseDto;
import com.statoverflow.status.domain.upload.service.UploadService;
import com.statoverflow.status.domain.users.dto.BasicUsersDto;
import com.statoverflow.status.global.annotation.CurrentUser;
import com.statoverflow.status.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
@Tag(name = "[업로드] Upload API", description = "파일 업로드 관련 API")
public class UploadController {

	private final UploadService uploadService;

	@Operation(summary = "이미지 업로드", description = "최대 5개의 이미지를 업로드하고 URL 목록을 반환합니다.")
	@PostMapping(value = "/images", consumes = "multipart/form-data")
	public ResponseEntity<ApiResponse<UploadResponseDto>> uploadImages(
		@RequestParam("files") List<MultipartFile> files,
		@Parameter(hidden = true) @CurrentUser BasicUsersDto user) {
		List<String> urls = uploadService.uploadImages(files);
		return ApiResponse.created(new UploadResponseDto(urls));
	}
}
