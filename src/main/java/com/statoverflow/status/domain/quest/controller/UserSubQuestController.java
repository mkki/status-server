package com.statoverflow.status.domain.quest.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.statoverflow.status.domain.quest.dto.SubQuestLogDto;
import com.statoverflow.status.domain.quest.dto.response.QuestHistoryByDateDto;
import com.statoverflow.status.domain.quest.dto.response.RewardResponseDto;
import com.statoverflow.status.domain.quest.dto.response.SubQuestResponseDto;
import com.statoverflow.status.domain.quest.service.interfaces.UsersSubQuestService;
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
@Tag(name = "[퀘스트] User Sub Quest API", description = "사용자 서브 퀘스트 관련 API")
public class UserSubQuestController {

	private final UsersSubQuestService usersSubQuestService;

	@Operation(summary = "오늘의 서브 퀘스트 조회", description = "오늘 인증 할 수 있는 모든 서브 퀘스트를 조회합니다.")
	@GetMapping("/today")
	public ResponseEntity<ApiResponse<List<SubQuestResponseDto.UsersSubQuestResponseDto>>> getTodaySubQuests(
		@Parameter(hidden = true) @CurrentUser BasicUsersDto user) {
		return ApiResponse.ok(usersSubQuestService.getTodaySubQuests(user.id()));
	}

	@Operation(summary = "메인 퀘스트 ID로 서브 퀘스트 조회", description = "특정 메인 퀘스트에 속한 인증할 수 있는 서브 퀘스트를 조회합니다.")
	@GetMapping("/{id}/today")
	public ResponseEntity<ApiResponse<List<SubQuestResponseDto.UsersSubQuestResponseDto>>> getTodaySubQuestsByMainQuestId(
		@Parameter(description = "메인 퀘스트 ID", required = true) @PathVariable Long id,
		@Parameter(hidden = true) @CurrentUser BasicUsersDto user) {
		return ApiResponse.ok(usersSubQuestService.getTodaySubQuests(user.id(), id));
	}

	@Operation(summary = "서브 퀘스트 완료", description = "서브 퀘스트를 완료 처리하고 경험치를 부여합니다.")
	@PostMapping("/sub")
	public ResponseEntity<ApiResponse<RewardResponseDto>> doSubQuest(
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "완료할 서브 퀘스트 정보", required = true)
		@RequestBody SubQuestLogDto dto,
		@Parameter(hidden = true) @CurrentUser BasicUsersDto user) {
		return ApiResponse.created(usersSubQuestService.doSubQuest(user.id(), dto));
	}

	@Operation(summary = "서브 퀘스트 완료 기록 수정", description = "서브 퀘스트 완료 기록을 수정합니다.")
	@PatchMapping("/sub")
	public ResponseEntity<ApiResponse<SubQuestLogDto>> editSubQuest(
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "수정할 서브 퀘스트 기록 정보", required = true)
		@RequestBody SubQuestLogDto dto,
		@Parameter(hidden = true) @CurrentUser BasicUsersDto user) {
		return ApiResponse.ok(usersSubQuestService.editSubQuest(user.id(), dto));
	}
}
