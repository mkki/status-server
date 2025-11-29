package com.statoverflow.status.domain.quest.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.statoverflow.status.domain.attribute.dto.AttributeDto;
import com.statoverflow.status.domain.master.entity.MainSubQuest;
import com.statoverflow.status.domain.master.entity.SubQuest;
import com.statoverflow.status.domain.quest.dto.request.RerollSubQuestRequestDto;
import com.statoverflow.status.domain.quest.dto.response.SubQuestResponseDto;
import com.statoverflow.status.domain.quest.enums.FrequencyType;
import com.statoverflow.status.domain.quest.repository.MainSubQuestRepository;
import com.statoverflow.status.domain.quest.service.interfaces.SubQuestService;
import com.statoverflow.status.domain.quest.service.interfaces.UsersSubQuestService;
import com.statoverflow.status.global.error.ErrorType;
import com.statoverflow.status.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 서브 퀘스트 관리 서비스
 *
 * 주요 기능:
 * - 사용자별 서브 퀘스트 조회 및 추천
 * - 서브 퀘스트 리롤 처리
 * - 우선순위 기반 퀘스트 선택
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubQuestServiceImpl implements SubQuestService {

	private final MainSubQuestRepository mainSubQuestRepository;
	private final UsersSubQuestService usersSubQuestService;
	private final QuestUtil questUtil;
	private final SubQuestDtoConverter dtoConverter;
	private final SubQuestRerollProcessor rerollProcessor;

	@Value("${status.quest.subquest.output_subquest_num}")
	private int OUTPUT_SUBQUEST_NUM;

	/**
	 * 메인 퀘스트에 속한 서브 퀘스트 목록 조회
	 *
	 * @param mainQuestId 메인 퀘스트 ID
	 * @param userId 사용자 ID
	 * @return 추천 서브 퀘스트 목록
	 */
	@Override
	public List<SubQuestResponseDto> getSubQuests(Long mainQuestId, Long userId) {
		log.info("서브 퀘스트 조회 시작 - mainQuestId: {}, userId: {}",
			mainQuestId, userId);

		List<SubQuestResponseDto> availableSubQuests = getAvailableSubQuests(mainQuestId, userId);
		List<SubQuestResponseDto> selectedSubQuests = questUtil.selectRandoms(availableSubQuests, OUTPUT_SUBQUEST_NUM);

		log.info("서브 퀘스트 조회 완료 - 선택된 개수: {}", selectedSubQuests.size());
		logSubQuestIds("최종 선택된 서브 퀘스트", selectedSubQuests);

		return selectedSubQuests;
	}

	/**
	 * 서브 퀘스트 리롤 처리
	 *
	 * @param rerollRequest 리롤 요청 정보
	 * @param userId 사용자 ID
	 * @return 리롤된 서브 퀘스트 목록
	 */
	@Override
	public List<SubQuestResponseDto> rerollSubQuestRequestDto(RerollSubQuestRequestDto rerollRequest, Long userId) {
		log.info("서브 퀘스트 리롤 시작 - userId: {}, 기존 선택: {}개",
			userId, rerollRequest.selectedSubQuests().size());

		validateRerollRequest(rerollRequest);

		List<SubQuestResponseDto> availableSubQuests = getAvailableSubQuests(
			rerollRequest.mainQuest(), userId);

		List<SubQuestResponseDto> rerolledSubQuests = rerollProcessor.processReroll(
			rerollRequest, availableSubQuests, OUTPUT_SUBQUEST_NUM);

		log.info("서브 퀘스트 리롤 완료 - 선택된 개수: {}", rerolledSubQuests.size());
		logSubQuestIds("리롤된 서브 퀘스트", rerolledSubQuests);

		return rerolledSubQuests;
	}

	// ==================== Private Methods ====================

	/**
	 * 사용자가 선택 가능한 서브 퀘스트 목록 조회
	 */
	private List<SubQuestResponseDto> getAvailableSubQuests(Long mainQuestId, Long userId) {
		// 1. 조건에 맞는 서브 퀘스트 조회
		List<MainSubQuest> availableSubQuests = mainSubQuestRepository.findAllByMainQuestId(mainQuestId);

		// 2. DTO 변환
		List<SubQuestResponseDto> subQuestDtos = dtoConverter.convertToResponseDtos(availableSubQuests);

		log.debug("사용 가능한 서브 퀘스트 조회 완료 - 후보: {}개, 사용 가능: {}개",
			availableSubQuests.size(), subQuestDtos.size());

		return subQuestDtos;
	}

	/**
	 * 조건에 맞는 후보 서브 퀘스트 조회
	 */
	private List<MainSubQuest> findCandidateSubQuests(List<Integer> attributes, Long mainQuestId) {
		int attributesBitmask = questUtil.calculateCombinedBitmask(attributes);
		List<MainSubQuest> candidates = mainSubQuestRepository.findAllByMainQuestIdAndAttributes(mainQuestId, attributesBitmask);

		log.debug("후보 서브 퀘스트 조회 완료 - 개수: {}", candidates.size());
		logMainSubQuestIds("후보 서브 퀘스트", candidates);

		return candidates;
	}

	/**
	 * 리롤 요청 유효성 검증
	 */
	private void validateRerollRequest(RerollSubQuestRequestDto dto) {
		int selectedCount = dto.selectedSubQuests().size();
		if (selectedCount >= OUTPUT_SUBQUEST_NUM) {
			log.warn("잘못된 리롤 요청 - 선택된 퀘스트 수: {}, 최대 출력 수: {}", selectedCount, OUTPUT_SUBQUEST_NUM);
			throw new CustomException(ErrorType.INVALID_SUBQUEST_SELECTED);
		}
	}

	// ==================== Logging Helper Methods ====================

	/**
	 * 서브 퀘스트 DTO 목록의 ID들을 로깅
	 */
	private void logSubQuestIds(String message, List<SubQuestResponseDto> subQuests) {
		List<Long> ids = subQuests.stream().map(SubQuestResponseDto::id).collect(Collectors.toList());
		log.debug("{} ID: {}", message, ids);
	}

	/**
	 * MainSubQuest 엔티티 목록의 서브 퀘스트 ID들을 로깅
	 */
	private void logMainSubQuestIds(String message, List<MainSubQuest> mainSubQuests) {
		List<Long> ids = mainSubQuests.stream()
			.map(msq -> msq.getSubQuest().getId())
			.collect(Collectors.toList());
		log.debug("{} ID: {}", message, ids);
	}
}

/**
 * 서브 퀘스트 DTO 변환 전용 서비스
 * 단일 책임 원칙에 따라 변환 로직을 분리
 */
@Service
@RequiredArgsConstructor
@Slf4j
class SubQuestDtoConverter {

	/**
	 * MainSubQuest 엔티티 리스트를 SubQuestResponseDto 리스트로 변환
	 */
	public List<SubQuestResponseDto> convertToResponseDtos(List<MainSubQuest> mainSubQuests) {
		return mainSubQuests.stream()
			.map(this::convertToResponseDto)
			.collect(Collectors.toList());
	}

	/**
	 * 단일 MainSubQuest 엔티티를 SubQuestResponseDto로 변환
	 */
	public SubQuestResponseDto convertToResponseDto(MainSubQuest mainSubQuest) {
		SubQuest subQuest = mainSubQuest.getSubQuest();

		// 속성 정보 생성
		List<AttributeDto> attributes = AttributeDto.fromMainSubQuest(mainSubQuest);

		// 랜덤 빈도 타입 선택
		FrequencyType frequencyType = FrequencyType.getRandomFrequencyType();

		// 액션 단위 정보 추출
		String actionUnitTypeUnit = subQuest.getActionUnitType().getUnit();
		int actionUnitNumValue = subQuest.getActionUnitType().getDefaultCount();

		// 설명 생성 (플레이스홀더 치환)
		String formattedDescription = formatQuestDescription(subQuest.getName(), actionUnitNumValue);

		log.debug("서브 퀘스트 DTO 변환 완료 - id: {}, frequencyType: {}", subQuest.getId(), frequencyType);

		return new SubQuestResponseDto(
			subQuest.getId(),
			frequencyType,
			actionUnitTypeUnit,
			actionUnitNumValue,
			attributes,
			formattedDescription
		);
	}

	/**
	 * 퀘스트 설명에서 플레이스홀더를 실제 값으로 치환
	 */
	private String formatQuestDescription(String template, int actionUnitNum) {
		String formatted = String.format(template, actionUnitNum);
		log.debug("퀘스트 설명 변환 - '{}' -> '{}'", template, formatted);
		return formatted;
	}
}

/**
 * 서브 퀘스트 리롤 처리 전용 서비스
 * 복잡한 리롤 로직을 별도 클래스로 분리하여 가독성 향상
 */
@Service
@RequiredArgsConstructor
@Slf4j
class SubQuestRerollProcessor {

	private final QuestUtil questUtil;

	/**
	 * 서브 퀘스트 리롤 처리
	 *
	 * @param rerollRequest 리롤 요청 정보
	 * @param availableSubQuests 사용 가능한 서브 퀘스트 목록
	 * @param outputQuestNum 출력할 퀘스트 개수
	 * @return 리롤된 서브 퀘스트 목록
	 */
	public List<SubQuestResponseDto> processReroll(RerollSubQuestRequestDto rerollRequest,
		List<SubQuestResponseDto> availableSubQuests,
		int outputQuestNum) {
		int rerollRequiredCount = outputQuestNum - rerollRequest.selectedSubQuests().size();

		// 기존 선택된 퀘스트 제외
		List<SubQuestResponseDto> candidateSubQuests = excludeSelectedSubQuests(
			availableSubQuests, rerollRequest.selectedSubQuests());

		// 우선순위 기반 퀘스트 선택
		return selectQuestsWithPriority(candidateSubQuests, rerollRequest.gottenSubQuests(), rerollRequiredCount);
	}

	/**
	 * 이미 선택된 서브 퀘스트를 후보 목록에서 제외
	 */
	private List<SubQuestResponseDto> excludeSelectedSubQuests(List<SubQuestResponseDto> allSubQuests,
		List<Long> selectedSubQuests) {
		Set<Long> excludeIds = Set.copyOf(selectedSubQuests);

		List<SubQuestResponseDto> candidates = questUtil.filterExcluding(
			allSubQuests, excludeIds, SubQuestResponseDto::id);

		log.debug("선택된 퀘스트 제외 완료 - 전체: {}개, 제외: {}개, 후보: {}개",
			allSubQuests.size(), excludeIds.size(), candidates.size());

		return candidates;
	}

	/**
	 * 우선순위 기반 퀘스트 선택
	 * 1순위: 새로운 퀘스트 (이전에 받지 않은 퀘스트)
	 * 2순위: 재사용 가능한 퀘스트 (이전에 받았던 퀘스트)
	 */
	private List<SubQuestResponseDto> selectQuestsWithPriority(List<SubQuestResponseDto> candidates,
		List<Long> gottenSubQuests,
		int requiredCount) {
		QuestPriorityGroups groups = categorizeQuestsByPriority(candidates, gottenSubQuests);

		log.debug("우선순위별 퀘스트 분류 - 새로운: {}개, 재사용 가능: {}개",
			groups.newQuests().size(), groups.reusableQuests().size());

		return selectFromPriorityGroups(groups, requiredCount);
	}

	/**
	 * 후보 퀘스트를 우선순위별로 분류
	 */
	private QuestPriorityGroups categorizeQuestsByPriority(List<SubQuestResponseDto> candidates,
		List<Long> gottenSubQuests) {
		Set<Long> gottenIds = Set.copyOf(gottenSubQuests);

		List<SubQuestResponseDto> newQuests = candidates.stream()
			.filter(quest -> !gottenIds.contains(quest.id()))
			.collect(Collectors.toList());

		List<SubQuestResponseDto> reusableQuests = candidates.stream()
			.filter(quest -> gottenIds.contains(quest.id()))
			.collect(Collectors.toList());

		return new QuestPriorityGroups(newQuests, reusableQuests);
	}

	/**
	 * 우선순위 그룹에서 필요한 개수만큼 선택
	 */
	private List<SubQuestResponseDto> selectFromPriorityGroups(QuestPriorityGroups groups, int requiredCount) {
		List<SubQuestResponseDto> selectedQuests = new ArrayList<>();

		// 1순위: 새로운 퀘스트에서 우선 선택
		int newQuestCount = Math.min(groups.newQuests().size(), requiredCount);
		selectedQuests.addAll(questUtil.selectRandoms(groups.newQuests(), newQuestCount));

		// 2순위: 부족한 만큼 재사용 퀘스트에서 선택
		int remainingCount = requiredCount - newQuestCount;
		if (remainingCount > 0) {
			selectedQuests.addAll(questUtil.selectRandoms(groups.reusableQuests(), remainingCount));
		}

		log.debug("우선순위 기반 선택 완료 - 새로운: {}개, 재사용: {}개",
			newQuestCount, remainingCount);

		return selectedQuests;
	}

	/**
	 * 우선순위별 퀘스트 그룹을 담는 레코드
	 *
	 * @param newQuests 새로운 퀘스트 목록 (이전에 받지 않은 퀘스트)
	 * @param reusableQuests 재사용 가능한 퀘스트 목록 (이전에 받았던 퀘스트)
	 */
	private record QuestPriorityGroups(
		List<SubQuestResponseDto> newQuests,
		List<SubQuestResponseDto> reusableQuests
	) {}
}