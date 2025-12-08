package com.statoverflow.status.domain.quest.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.statoverflow.status.domain.quest.dto.WithStatus;
import com.statoverflow.status.domain.quest.dto.response.UsersMainQuestResponseDto;
import com.statoverflow.status.domain.quest.service.interfaces.UsersMainQuestService;
import com.statoverflow.status.domain.users.dto.BasicUsersDto;
import com.statoverflow.status.global.annotation.CurrentUser;
import com.statoverflow.status.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/user-quest")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "[퀘스트] User Main Quest API", description = "사용자 메인 퀘스트 관련 API")
public class UserMainQuestController {

	private final UsersMainQuestService usersMainQuestService;

	@Operation(summary = "나의 메인 퀘스트 목록 조회", description = "현재 유저가 진행 중인 모든 메인 퀘스트를 조회합니다.")
	@GetMapping("/me")
	public ResponseEntity<ApiResponse<List<UsersMainQuestResponseDto>>> getUsersMainQuests(
		@Parameter(hidden = true) @CurrentUser BasicUsersDto user) {
		return ApiResponse.ok(usersMainQuestService.getUsersMainQuests(user.id()));
	}

	@Operation(summary = "메인 퀘스트 삭제", description = "특정 메인 퀘스트를 삭제합니다.")
	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<?>> deleteMainQuest(
		@Parameter(description = "삭제할 메인 퀘스트 ID", required = true) @PathVariable Long id,
		@Parameter(hidden = true) @CurrentUser BasicUsersDto user) {
		usersMainQuestService.deleteMainQuest(id);
		return ApiResponse.noContent();
	}

	@Operation(summary = "[퀘스트 조회 - 3] 메인 퀘스트 ID로 메인 퀘스트 정보 조회", description = "특정 메인 퀘스트에 대한 정보를 조회합니다.")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<WithStatus<UsersMainQuestResponseDto>>> getUsersMainQuestById(
		@Parameter(description = "메인 퀘스트 ID", required = true) @PathVariable Long id,
		@Parameter(hidden = true) @CurrentUser BasicUsersDto user) {
		return ApiResponse.ok(usersMainQuestService.getUsersMainQuestById(user.id(), id));
	}

}
