package com.statoverflow.status.domain.quest.service;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.statoverflow.status.domain.attribute.repository.AttributeRepository;
import com.statoverflow.status.domain.master.entity.Attribute;
import com.statoverflow.status.domain.master.entity.QuestTheme;
import com.statoverflow.status.domain.quest.dto.response.ThemeResponseDto;
import com.statoverflow.status.domain.quest.service.interfaces.ThemeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 퀘스트 테마 관련 비즈니스 로직을 처리하는 서비스 클래스
 *
 * 주요 기능:
 * 1. 속성 기반 테마 조회 및 랜덤 선택
 * 2. 기존 테마를 제외한 리롤 기능
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThemeServiceImpl implements ThemeService {

	private final AttributeRepository attributeRepository;
	@Value("${status.quest.theme.output_theme_num}")
	private int OUTPUT_THEME_NUM;

	private final QuestUtil questUtil;

	/**
	 * 주어진 속성들에 매칭되는 테마들 중에서 랜덤하게 선택하여 반환
	 *
	 * @param attributes 테마 선택 기준이 되는 속성 목록
	 * @return 랜덤 선택된 테마 응답 DTO 목록
	 */
	@Override
	public List<ThemeResponseDto> getThemes() {

		List<Integer> attributes = getAllAttributes();

		List<ThemeResponseDto> candidateThemes = getCandidateThemes(attributes);
		List<ThemeResponseDto> selectedThemes = selectRandomThemes(candidateThemes);

		logFinalSelection("테마 조회", selectedThemes);
		return selectedThemes;
	}

	private List<Integer> getAllAttributes() {
		return attributeRepository.findAll().stream().map(Attribute::getId).collect(Collectors.toList());
	}

	/**
	 * 특정 테마들을 제외하고 새로운 테마들을 랜덤하게 선택하여 반환 (리롤)
	 *
	 * @param attributes 테마 선택 기준이 되는 속성 목록
	 * @param themesToExclude 제외할 테마 ID 목록
	 * @return 리롤된 테마 응답 DTO 목록
	 */
	@Override
	public List<ThemeResponseDto> rerollThemes(List<Integer> themesToExclude) {

		List<Integer> attributes = getAllAttributes();

		log.info("테마 리롤 시작 - 속성: {}, 제외할 테마: {}", attributes, themesToExclude);

		List<ThemeResponseDto> candidateThemes = getCandidateThemes(attributes);
		Set<Long> excludeIds = convertToLongSet(themesToExclude);

		List<ThemeResponseDto> selectedThemes = selectRerollThemes(candidateThemes, excludeIds);

		logFinalSelection("테마 리롤", selectedThemes);
		return selectedThemes;
	}

	/**
	 * 속성 기준으로 후보 테마들을 조회하고 DTO로 변환
	 *
	 * @param attributes 조회 기준 속성 목록
	 * @return 후보 테마 DTO 목록
	 */
	private List<ThemeResponseDto> getCandidateThemes(List<Integer> attributes) {
		List<QuestTheme> matchingThemes = questUtil.getAllThemes();
		log.info("속성 매칭 테마 조회 완료 - 총 {}개", matchingThemes.size());
		log.debug("조회된 테마 ID: {}", this.extractIdsAtQuestTheme(matchingThemes));

		return convertToResponseDtos(matchingThemes);
	}

	/**
	 * 리롤 시 제외 테마를 고려하여 테마 선택
	 *
	 * @param candidateThemes 전체 후보 테마 목록
	 * @param excludeIds 제외할 테마 ID Set
	 * @return 선택된 테마 목록
	 */
	private List<ThemeResponseDto> selectRerollThemes(List<ThemeResponseDto> candidateThemes, Set<Long> excludeIds) {
		// 제외 테마를 필터링한 사용 가능한 테마 목록
		List<ThemeResponseDto> availableThemes = candidateThemes.stream()
			.filter(theme -> !excludeIds.contains(theme.id()))
			.collect(Collectors.toList());

		log.debug("제외 필터링 후 사용 가능한 테마 - {}개", availableThemes.size());

		// 사용 가능한 테마가 충분한 경우
		if (availableThemes.size() >= OUTPUT_THEME_NUM) {
			log.debug("충분한 테마 존재 - 제외된 테마만으로 선택");
			return selectRandomThemes(availableThemes);
		}

		// 부족한 경우: availableThemes를 모두 포함하고, 부족한 개수만큼 제외된 테마에서 추가
		log.debug("테마 부족으로 제외된 테마에서 추가 선택 - available: {}개, 필요: {}개",
			availableThemes.size(), OUTPUT_THEME_NUM);

		List<ThemeResponseDto> result = new ArrayList<>(questUtil.selectRandoms(availableThemes, availableThemes.size()));

		// 제외된 테마들만 추출
		List<ThemeResponseDto> excludedThemes = candidateThemes.stream()
			.filter(theme -> excludeIds.contains(theme.id()))
			.collect(Collectors.toList());

		// 부족한 개수 계산
		int needMore = OUTPUT_THEME_NUM - availableThemes.size();

		// 제외된 테마에서 부족한 개수만큼 랜덤 선택하여 추가
		List<ThemeResponseDto> additionalThemes = questUtil.selectRandoms(excludedThemes, needMore);
		result.addAll(additionalThemes);

		log.debug("최종 선택: available {}개 + excluded에서 {}개 = 총 {}개",
			availableThemes.size(), additionalThemes.size(), result.size());

		return result;
	}

	/**
	 * QuestTheme 엔티티 목록을 ThemeResponseDto 목록으로 변환
	 *
	 * @param themes QuestTheme 엔티티 목록
	 * @return ThemeResponseDto 목록
	 */
	private List<ThemeResponseDto> convertToResponseDtos(List<QuestTheme> themes) {
		List<ThemeResponseDto> responseDtos = themes.stream()
			.map(theme -> new ThemeResponseDto(theme.getId(), theme.getName()))
			.collect(Collectors.toList());

		log.debug("DTO 변환 완료 - {}개", responseDtos.size());
		return responseDtos;
	}

	/**
	 * Integer 목록을 Long Set으로 변환
	 *
	 * @param integerIds Integer ID 목록
	 * @return Long ID Set
	 */
	private Set<Long> convertToLongSet(List<Integer> integerIds) {
		Set<Long> longIds = integerIds.stream()
			.map(Integer::longValue)
			.collect(Collectors.toSet());
		log.debug("제외할 테마 ID: {}", longIds);
		return longIds;
	}

	/**
	 * 후보 테마 목록에서 랜덤하게 선택
	 *
	 * @param candidates 후보 테마 목록
	 * @return 랜덤 선택된 테마 목록
	 */
	private List<ThemeResponseDto> selectRandomThemes(List<ThemeResponseDto> candidates) {
		return questUtil.selectRandoms(candidates, OUTPUT_THEME_NUM);
	}

	/**
	 * QuestTheme 목록에서 ID 추출 (로깅용)
	 */
	private List<Long> extractIdsAtQuestTheme(List<QuestTheme> themes) {
		return themes.stream().map(QuestTheme::getId).collect(Collectors.toList());
	}

	/**
	 * ThemeResponseDto 목록에서 ID 추출 (로깅용)
	 */
	private List<Long> extractIdsAtThemeResponseDto(List<ThemeResponseDto> themes) {
		return themes.stream().map(ThemeResponseDto::id).collect(Collectors.toList());
	}

	/**
	 * 최종 선택 결과 로깅
	 */
	private void logFinalSelection(String operation, List<ThemeResponseDto> selectedThemes) {
		log.info("{} 완료 - 선택된 테마 {}개", operation, selectedThemes.size());
		log.info("선택된 테마 ID: {}", extractIdsAtThemeResponseDto(selectedThemes));
	}
}