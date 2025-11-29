package com.statoverflow.status.domain.quest.service;

import static com.statoverflow.status.global.error.ErrorType.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.statoverflow.status.domain.attribute.repository.AttributeRepository;
import com.statoverflow.status.domain.master.entity.Attribute;
import com.statoverflow.status.domain.master.entity.QuestTheme;
import com.statoverflow.status.domain.quest.repository.ThemeRepository;
import com.statoverflow.status.global.error.ErrorType;
import com.statoverflow.status.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 퀘스트 관련 공통 유틸리티 클래스
 *
 * 주요 기능:
 * - 속성(Attribute) 비트마스크 계산
 * - 랜덤 선택 로직
 * - 테마 조회 및 검증
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class QuestUtil {

	private final AttributeRepository attributeRepository;
	private final ThemeRepository themeRepository;
	private final Random random;

	@Value("${status.quest.attribute.select_attribute_num}")
	private int SELECTED_ATTRIBUTE_NUM;

	/**
	 * 속성 ID 리스트를 비트마스크로 변환
	 *
	 * @param attributes 속성 ID 리스트
	 * @return 조합된 비트마스크 값
	 * @throws CustomException 속성이 유효하지 않은 경우
	 */
	public int calculateCombinedBitmask(List<Integer> attributes) {
		// validateAttributes(attributes);

		int combinedBitmask = attributes.stream()
			.mapToInt(this::getAttributeBitmask)
			.reduce(0, (acc, bitMask) -> acc | bitMask);

		log.debug("속성 비트마스크 계산 완료 - attributes: {}, bitmask: {}", attributes, combinedBitmask);
		return combinedBitmask;
	}

	/**
	 * 주어진 속성들과 일치하는 모든 퀘스트 테마 조회
	 *
	 * @param attributes 속성 ID 리스트
	 * @return 조건에 맞는 퀘스트 테마 리스트
	 */
	public List<QuestTheme> getAllMatchingThemesByAttributes(List<Integer> attributes) {
		log.debug("속성별 테마 조회 시작 - attributes: {}", attributes);

		int combinedBitmask = calculateCombinedBitmask(attributes);
		List<QuestTheme> questThemes = themeRepository.findAllByAttributes(combinedBitmask);

		log.info("속성별 테마 조회 완료 - 조회된 테마 개수: {}", questThemes.size());
		return questThemes;
	}


	public List<QuestTheme> getAllThemes() {
		return themeRepository.findAll();
	}

	/**
	 * 리스트에서 지정된 개수만큼 랜덤하게 선택
	 *
	 * @param <T> 선택할 아이템의 타입
	 * @param items 선택 대상 아이템 리스트
	 * @param count 선택할 개수
	 * @return 랜덤하게 선택된 아이템 리스트
	 */
	public <T> List<T> selectRandoms(List<T> items, int count) {
		if (items.isEmpty()) {
			log.debug("빈 리스트에서 랜덤 선택 요청 - 빈 리스트 반환");
			return Collections.emptyList();
		}

		if (count <= 0) {
			log.debug("선택 개수가 0 이하 - count: {}, 빈 리스트 반환", count);
			return Collections.emptyList();
		}

		// 수정: ArrayList로 복사하여 mutable 리스트 생성
		List<T> mutableList = new ArrayList<>(items);

		if (count >= items.size()) {
			log.debug("요청 개수가 전체 개수 이상 - 전체 리스트 셔플하여 반환");
			Collections.shuffle(mutableList, random);
			return mutableList;
		}

		Collections.shuffle(mutableList, random);

		List<T> selected = mutableList.stream()
			.limit(count)
			.collect(Collectors.toList());

		log.debug("랜덤 선택 완료 - 전체: {}개, 선택: {}개", items.size(), selected.size());
		return selected;
	}

	/**
	 * 두 리스트에서 제외 조건에 따라 필터링
	 *
	 * @param <T> 아이템 타입
	 * @param source 원본 리스트
	 * @param excludeIds 제외할 ID 집합
	 * @param idExtractor ID 추출 함수
	 * @return 필터링된 리스트
	 */
	public <T, ID> List<T> filterExcluding(List<T> source, Set<ID> excludeIds,
		java.util.function.Function<T, ID> idExtractor) {
		List<T> filtered = source.stream()
			.filter(item -> !excludeIds.contains(idExtractor.apply(item)))
			.collect(Collectors.toList());

		log.debug("제외 필터링 완료 - 원본: {}개, 제외: {}개, 결과: {}개",
			source.size(), excludeIds.size(), filtered.size());
		return filtered;
	}

	// ==================== Private Helper Methods ====================

	/**
	 * 속성 리스트 유효성 검증
	 */
	private void validateAttributes(List<Integer> attributes) {
		if (attributes == null || attributes.isEmpty()) {
			log.warn("속성 리스트가 null 또는 비어있음");
			throw new CustomException(INVALID_ATTRIBUTES);
		}

		if (attributes.size() > SELECTED_ATTRIBUTE_NUM) {
			log.warn("속성 개수 초과 - 최대: {}, 입력: {}", SELECTED_ATTRIBUTE_NUM, attributes.size());
			throw new CustomException(INVALID_ATTRIBUTES);
		}
	}

	/**
	 * 속성 ID로부터 비트마스크 값 조회
	 */
	private int getAttributeBitmask(Integer attributeId) {
		return attributeRepository.findById(attributeId)
			.map(Attribute::getBitMask)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 속성 ID - attributeId: {}", attributeId);
				return new CustomException(INVALID_ATTRIBUTES);
			});
	}

}