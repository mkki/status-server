package com.statoverflow.status.domain.quest.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.statoverflow.status.domain.quest.dto.WithStatus;
import com.statoverflow.status.domain.quest.dto.response.QuestHistoryByDateDto;
import com.statoverflow.status.domain.quest.dto.response.UserQuestStatisticsDto;
import com.statoverflow.status.domain.quest.dto.response.UsersMainQuestResponseDto;
import com.statoverflow.status.domain.quest.service.interfaces.UsersMainQuestService;
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
@Tag(name = "[퀘스트] Quest Statistics API", description = "사용자 퀘스트 히스토리 관련 API")
public class QuestStatisticsController {

	private final UsersMainQuestService usersMainQuestService;
	private final UsersSubQuestService usersSubQuestService;

	@Operation(summary = "누적 기록 보기", description = "완료한 퀘스트의 데이터를 총합하여 누적 기록을 출력합니다.")
	@GetMapping("/user-statistics")
	public ResponseEntity<ApiResponse<UserQuestStatisticsDto>> getUserStatics(@CurrentUser BasicUsersDto user) {
		return ApiResponse.ok(usersMainQuestService.getUserStatistics(user.id()));
	}

	@Operation(summary = "완료한 메인 퀘스트 보기", description = "완료한 메인퀘스트를 ‘완료일 기준 최근 순’으로 상단부터 하단 방향으로 출력합니다.")
	@GetMapping("/history")
	public ResponseEntity<ApiResponse<List<WithStatus<UsersMainQuestResponseDto>>>> getUsersMainQuestHistory(
		@CurrentUser BasicUsersDto user) {
		return ApiResponse.ok(usersMainQuestService.getUsersMainQuestHistory(user.id()));
	}

	@Operation(summary = "[퀘스트 상세 조회 - 2] 메인 퀘스트 ID로 서브 퀘스트 완료 기록 조회", description = "특정 메인 퀘스트에 속한 모든 서브 퀘스트 완료 기록을 조회합니다.")
	@GetMapping("/history/{id}")
	public ResponseEntity<ApiResponse<List<QuestHistoryByDateDto>>> getSubQuestsLogsByMainQuestId(
		@Parameter(description = "메인 퀘스트 ID", required = true) @PathVariable Long id,
		@Parameter(hidden = true) @CurrentUser BasicUsersDto user) {
		return ApiResponse.ok(usersSubQuestService.getSubQuestsLogs(user.id(), id));
	}
}
