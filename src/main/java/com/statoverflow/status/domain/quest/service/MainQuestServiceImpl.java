package com.statoverflow.status.domain.quest.service;

import static org.springframework.data.domain.Sort.Direction.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.statoverflow.status.domain.master.entity.MainQuest;
import com.statoverflow.status.domain.quest.dto.response.MainQuestResponseDto;
import com.statoverflow.status.domain.quest.entity.UsersMainQuest;
import com.statoverflow.status.domain.quest.enums.QuestStatus;
import com.statoverflow.status.domain.quest.repository.MainQuestRepository;
import com.statoverflow.status.domain.quest.service.interfaces.MainQuestService;
import com.statoverflow.status.domain.quest.service.interfaces.UsersMainQuestService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 메인 퀘스트 관리 서비스
 *
 * 주요 기능:
 * - 사용자별 메인 퀘스트 조회 및 추천
 * - 퀘스트 리롤 처리
 * - 진행 중인 퀘스트 필터링
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MainQuestServiceImpl implements MainQuestService {

	private final MainQuestRepository mainQuestRepository;
	private final QuestUtil questUtil;
	private final MainQuestFilterService filterService;

	@Value("${status.quest.mainquest.output_mainquest_num}")
	private int OUTPUT_MAINQUEST_NUM;

	/**
	 * 사용자 조건에 맞는 메인 퀘스트 목록 조회
	 *
	 * @param userId 사용자 ID
	 * @param themeId 테마 ID
	 * @return 추천 메인 퀘스트 목록
	 */
	@Override
	public List<MainQuestResponseDto> getMainQuests(Long userId, Long themeId) {
		log.info("메인 퀘스트 조회 시작 - userId: {}, themeId: {}, attributes: {}", userId, themeId);

		List<MainQuestResponseDto> availableQuests = getAvailableMainQuests(userId, themeId);
		List<MainQuestResponseDto> selectedQuests = questUtil.selectRandoms(availableQuests, OUTPUT_MAINQUEST_NUM);

		log.info("메인 퀘스트 조회 완료 - 선택된 개수: {}", selectedQuests.size());
		logQuestIds("최종 선택된 메인 퀘스트", selectedQuests);

		return selectedQuests;
	}

	/**
	 * 메인 퀘스트 리롤 처리
	 *
	 * @param attributes 선택된 속성 리스트
	 * @param mainQuestsToExclude 제외할 퀘스트 ID 리스트
	 * @param userId 사용자 ID
	 * @param themeId 테마 ID
	 * @return 리롤된 메인 퀘스트 목록
	 */
	@Override
	public List<MainQuestResponseDto> rerollMainQuests(List<Long> mainQuestsToExclude,
		Long userId, Long themeId) {
		log.info("메인 퀘스트 리롤 시작 - userId: {}, themeId: {}, 제외 개수: {}", userId, themeId, mainQuestsToExclude.size());

		List<MainQuestResponseDto> availableQuests = getAvailableMainQuests(userId, themeId);
		List<MainQuestResponseDto> rerolledQuests = processReroll(availableQuests, mainQuestsToExclude);

		log.info("메인 퀘스트 리롤 완료 - 선택된 개수: {}", rerolledQuests.size());
		logQuestIds("리롤된 메인 퀘스트", rerolledQuests);

		return rerolledQuests;
	}

	// ==================== Private Methods ====================

	/**
	 * 사용자가 선택 가능한 메인 퀘스트 목록 조회
	 */
	private List<MainQuestResponseDto> getAvailableMainQuests(Long userId, Long themeId) {
		// 1. 조건에 맞는 모든 퀘스트 조회
		List<MainQuest> candidateQuests = mainQuestRepository.findAllByThemeId(themeId);

		// 2. 진행 중인 퀘스트 제외
		List<MainQuest> availableQuests = filterService.excludeUserActiveQuests(candidateQuests, userId);

		// 3. DTO 변환
		List<MainQuestResponseDto> questDtos = convertToResponseDtos(availableQuests);

		log.debug("사용 가능한 메인 퀘스트 조회 완료 - 전체 후보: {}개, 사용 가능: {}개",
			candidateQuests.size(), questDtos.size());

		return questDtos;
	}

	/**
	 * 조건에 맞는 후보 퀘스트 조회
	 */
	private List<MainQuest> findCandidateMainQuests(List<Integer> attributes, Long themeId) {
		int attributesBitmask = questUtil.calculateCombinedBitmask(attributes);
		List<MainQuest> candidates = mainQuestRepository.findAllByThemeIdAndAttributes(themeId, attributesBitmask);

		log.debug("후보 메인 퀘스트 조회 완료 - 개수: {}", candidates.size());
		logEntityIds("후보 메인 퀘스트", candidates);

		return candidates;
	}

	/**
	 * 리롤 처리 로직
	 * 필터링된 퀘스트는 무조건 포함하고, 부족한 개수만 제외된 퀘스트에서 추가 선택
	 */
	private List<MainQuestResponseDto> processReroll(List<MainQuestResponseDto> availableQuests,
		List<Long> excludeIds) {
		Set<Long> excludeSet = Set.copyOf(excludeIds);

		// 제외할 퀘스트 필터링 (무조건 포함되어야 할 퀘스트들)
		List<MainQuestResponseDto> filteredQuests = questUtil.filterExcluding(
			availableQuests, excludeSet, MainQuestResponseDto::id);

		log.debug("리롤 필터링 완료 - 필터링된 퀘스트: {}개", filteredQuests.size());

		// 충분한 퀘스트가 있으면 필터링된 것만 사용
		if (filteredQuests.size() >= OUTPUT_MAINQUEST_NUM) {
			return questUtil.selectRandoms(filteredQuests, OUTPUT_MAINQUEST_NUM);
		}

		// 부족하면 필터링된 퀘스트 + 제외된 퀘스트에서 추가 선택
		return handleInsufficientQuests(availableQuests, filteredQuests, excludeSet);
	}

	/**
	 * 리롤 시 퀘스트가 부족한 경우 처리
	 * 필터링된 퀘스트는 무조건 포함하고, 부족한 개수만 제외된 퀘스트에서 추가 선택
	 */
	private List<MainQuestResponseDto> handleInsufficientQuests(List<MainQuestResponseDto> allQuests,
		List<MainQuestResponseDto> filteredQuests, Set<Long> excludeSet) {

		int needMore = OUTPUT_MAINQUEST_NUM - filteredQuests.size();
		log.debug("리롤 퀘스트 부족 - 필터링된 퀘스트: {}개, 추가 필요: {}개", filteredQuests.size(), needMore);

		// 제외된 퀘스트들 중에서 추가로 선택
		List<MainQuestResponseDto> excludedQuests = allQuests.stream()
			.filter(quest -> excludeSet.contains(quest.id()))
			.collect(Collectors.toList());

		List<MainQuestResponseDto> additionalQuests = questUtil.selectRandoms(excludedQuests, needMore);
		log.debug("제외된 퀘스트에서 추가 선택 완료 - {}개", additionalQuests.size());

		// 필터링된 퀘스트 + 추가 선택된 퀘스트 조합
		List<MainQuestResponseDto> finalQuests = new ArrayList<>(questUtil.selectRandoms(filteredQuests, filteredQuests.size()));
		finalQuests.addAll(additionalQuests);

		return finalQuests;
	}

	/**
	 * 엔티티 리스트를 응답 DTO 리스트로 변환
	 */
	private List<MainQuestResponseDto> convertToResponseDtos(List<MainQuest> mainQuests) {
		return mainQuests.stream()
			.map(quest -> new MainQuestResponseDto(quest.getId(), quest.getName()))
			.collect(Collectors.toList());
	}

	// ==================== Logging Helper Methods ====================

	/**
	 * 퀘스트 DTO 목록의 ID들을 로깅
	 */
	private void logQuestIds(String message, List<MainQuestResponseDto> quests) {
		List<Long> ids = quests.stream().map(MainQuestResponseDto::id).collect(Collectors.toList());
		log.debug("{} ID: {}", message, ids);
	}

	/**
	 * 엔티티 목록의 ID들을 로깅
	 */
	private void logEntityIds(String message, List<MainQuest> quests) {
		List<Long> ids = quests.stream().map(MainQuest::getId).collect(Collectors.toList());
		log.debug("{} ID: {}", message, ids);
	}
}

/**
 * 메인 퀘스트 필터링 전용 서비스
 * 단일 책임 원칙에 따라 필터링 로직을 분리
 */
@Service
@RequiredArgsConstructor
@Slf4j
class MainQuestFilterService {

	private final UsersMainQuestService usersMainQuestService;

	/**
	 * 사용자가 진행 중인 퀘스트를 제외한 퀘스트 목록 반환
	 */
	public List<MainQuest> excludeUserActiveQuests(List<MainQuest> candidates, Long userId) {
		Set<MainQuest> userActiveQuests = getUserActiveMainQuests(userId);

		List<MainQuest> availableQuests = candidates.stream()
			.filter(quest -> !userActiveQuests.contains(quest))
			.collect(Collectors.toList());

		log.debug("진행 중인 퀘스트 필터링 완료 - 진행 중: {}개, 사용 가능: {}개",
			userActiveQuests.size(), availableQuests.size());

		return availableQuests;
	}

	/**
	 * 사용자가 진행 중인 메인 퀘스트 집합 조회
	 */
	private Set<MainQuest> getUserActiveMainQuests(Long userId) {
	return usersMainQuestService.getUsersMainQuestByUserIdAndStatus(userId, List.of(QuestStatus.ACTIVE), Sort.by(DESC, "id"))
			.stream()
			.map(UsersMainQuest::getMainQuest)
			.collect(Collectors.toSet());
	}
}