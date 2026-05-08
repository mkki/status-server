package com.statoverflow.status.domain.quest.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.statoverflow.status.domain.attribute.dto.AttributeDto;
import com.statoverflow.status.domain.attribute.service.AttributeService;
import com.statoverflow.status.domain.quest.dto.SubQuestLogDto;
import com.statoverflow.status.domain.quest.dto.response.QuestHistoryByDateDto;
import com.statoverflow.status.domain.quest.dto.response.RewardResponseDto;
import com.statoverflow.status.domain.quest.dto.response.SubQuestResponseDto;
import com.statoverflow.status.domain.quest.entity.UsersMainQuest;
import com.statoverflow.status.domain.quest.entity.UsersSubQuest;
import com.statoverflow.status.domain.quest.entity.UsersSubQuestLog;
import com.statoverflow.status.domain.quest.enums.FrequencyType;
import com.statoverflow.status.domain.quest.enums.QuestStatus;
import com.statoverflow.status.domain.quest.repository.UsersSubQuestLogRepository;
import com.statoverflow.status.domain.quest.repository.UsersSubQuestRepository;
import com.statoverflow.status.domain.quest.service.interfaces.UsersSubQuestService;
import com.statoverflow.status.domain.users.enums.SourceType;
import com.statoverflow.status.global.error.ErrorType;
import com.statoverflow.status.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 사용자 서브 퀘스트 관리 서비스
 *
 * 주요 기능:
 * - 서브 퀘스트 조회 및 완료 처리
 * - 퀘스트 히스토리 관리
 * - 완료 조건 검증 및 보상 지급
 * - 메인 퀘스트 완료 상태 확인
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UsersSubQuestServiceImpl implements UsersSubQuestService {

	private final UsersSubQuestRepository usersSubQuestRepository;
	private final UsersSubQuestLogRepository usersSubQuestLogRepository;
	private final AttributeService attributeService;

	/**
	 * 사용자의 모든 오늘 할 수 있는 서브 퀘스트를 조회합니다.
	 *
	 * @param userId 사용자 ID
	 * @return 오늘 수행 가능한 서브 퀘스트 목록
	 */
	@Override
	public List<SubQuestResponseDto.UsersSubQuestResponseDto> getTodaySubQuests(Long userId) {
		List<UsersSubQuest> activeSubQuests = findActiveSubQuests(userId);
		return convertToSubQuestResponseDtos(activeSubQuests);
	}

	/**
	 * 특정 메인 퀘스트의 오늘 할 수 있는 서브 퀘스트를 조회합니다.
	 *
	 * @param userId 사용자 ID
	 * @param mainQuestId 메인 퀘스트 ID
	 * @return 해당 메인 퀘스트의 오늘 수행 가능한 서브 퀘스트 목록
	 */
	@Override
	public List<SubQuestResponseDto.UsersSubQuestResponseDto> getTodaySubQuests(Long userId, Long mainQuestId) {
		List<UsersSubQuest> activeSubQuests = findActiveSubQuestsByMainQuest(userId, mainQuestId);
		return convertToSubQuestResponseDtos(activeSubQuests);
	}

	/**
	 * 특정 메인 퀘스트의 서브 퀘스트 히스토리를 날짜별로 조회합니다.
	 *
	 * @param userId 사용자 ID
	 * @param mainQuestId 메인 퀘스트 ID
	 * @return 날짜별 퀘스트 히스토리
	 */
	@Override
	public List<QuestHistoryByDateDto> getSubQuestsLogs(Long userId, Long mainQuestId) {

		List<UsersSubQuest> subQuests = findSubQuestsWithHistory(userId, mainQuestId);
		List<UsersSubQuestLog> allLogs = collectAllSubQuestLogs(subQuests);

		return groupLogsByDate(allLogs);
	}

	/**
	 * 서브 퀘스트를 완료하고 보상을 지급합니다.
	 *
	 * @param userId 사용자 ID
	 * @param logDto 퀘스트 완료 로그 정보
	 * @return 지급된 보상 정보
	 */
	@Override
	public RewardResponseDto doSubQuest(Long userId, SubQuestLogDto logDto) {
		log.debug("서브 퀘스트 완료 처리 시작 - userId: {}, subQuestId: {}", userId, logDto.id());

		// 1. 서브 퀘스트 조회 및 검증
		UsersSubQuest subQuest = findActiveSubQuestForCompletion(userId, logDto.id());

		// 2. 완료 로그 생성
		createSubQuestLog(subQuest, logDto);

		// 3. 서브 퀘스트 보상 지급
		List<AttributeDto> subQuestRewards = grantSubQuestRewards(subQuest);

		// 4. 서브 퀘스트 상태 업데이트
		updateSubQuestStatus(subQuest);

		// 5. 메인 퀘스트 완료 여부 확인 및 보상 지급
		MainQuestCompletionResult mainQuestResult = checkAndCompleteMainQuest(subQuest.getMainQuest());

		return new RewardResponseDto(subQuestRewards, mainQuestResult.rewards(), mainQuestResult.completed());
	}

	/**
	 * 서브 퀘스트 로그를 수정합니다.
	 *
	 * @param userId 사용자 ID
	 * @param logDto 수정할 로그 정보
	 * @return 수정된 로그 정보
	 */
	@Override
	public SubQuestLogDto editSubQuest(Long userId, SubQuestLogDto logDto) {
		UsersSubQuestLog log = findSubQuestLogById(logDto.id());
		updateSubQuestLog(log, logDto);

		return new SubQuestLogDto(log.getId(), log.getDifficulty(), log.getMemo(), log.getImageUrls());
	}

	// ==================== Private Helper Methods ====================

	/**
	 * 사용자의 활성 서브 퀘스트를 조회합니다.
	 */
	private List<UsersSubQuest> findActiveSubQuests(Long userId) {
		return usersSubQuestRepository.findByUsersIdAndStatus(userId, QuestStatus.ACTIVE);
	}

	/**
	 * 특정 메인 퀘스트의 활성 서브 퀘스트를 조회합니다.
	 */
	private List<UsersSubQuest> findActiveSubQuestsByMainQuest(Long userId, Long mainQuestId) {
		return usersSubQuestRepository.findByUsersIdAndMainQuestIdAndStatus(userId, mainQuestId, QuestStatus.ACTIVE);
	}

	/**
	 * 히스토리 조회용 서브 퀘스트를 조회합니다.
	 */
	private List<UsersSubQuest> findSubQuestsWithHistory(Long userId, Long mainQuestId) {
		return usersSubQuestRepository.findByUsersIdAndMainQuestId(userId, mainQuestId);
	}

	/**
	 * 완료 가능한 활성 서브 퀘스트를 조회합니다.
	 */
	private UsersSubQuest findActiveSubQuestForCompletion(Long userId, Long subQuestId) {
		return usersSubQuestRepository.findByIdAndUsersIdAndStatus(subQuestId, userId, QuestStatus.ACTIVE)
			.orElseThrow(() -> new CustomException(ErrorType.COMPLETED_SUBQUEST));
	}

	/**
	 * 서브 퀘스트 로그를 ID로 조회합니다.
	 */
	private UsersSubQuestLog findSubQuestLogById(Long logId) {
		return usersSubQuestLogRepository.findById(logId)
			.orElseThrow(() -> new CustomException(ErrorType.SUBQUESTLOG_NOT_FOUND));
	}

	/**
	 * 서브 퀘스트 목록을 응답 DTO로 변환합니다.
	 */
	private List<SubQuestResponseDto.UsersSubQuestResponseDto> convertToSubQuestResponseDtos(List<UsersSubQuest> subQuests) {
		return subQuests.stream()
			.map(this::convertToUsersSubQuestResponseDto)
			.collect(Collectors.toList());
	}

	/**
	 * 단일 서브 퀘스트를 응답 DTO로 변환합니다.
	 */
	private SubQuestResponseDto.UsersSubQuestResponseDto convertToUsersSubQuestResponseDto(UsersSubQuest subQuest) {
		SubQuestResponseDto baseDto = convertToSubQuestResponseDto(subQuest);
		QuestProgressInfo progressInfo = calculateQuestProgressInfo(subQuest);

		return new SubQuestResponseDto.UsersSubQuestResponseDto(
			subQuest.getMainQuest().getId(),
			baseDto,
			progressInfo.repeatCount(),
			progressInfo.essential()
		);
	}

	/**
	 * 기본 서브 퀘스트 정보를 DTO로 변환합니다.
	 */
	private SubQuestResponseDto convertToSubQuestResponseDto(UsersSubQuest subQuest) {
		List<AttributeDto> attributes = AttributeDto.fromUsersSubQuest(subQuest);

		return new SubQuestResponseDto(
			subQuest.getId(),
			subQuest.getFrequencyType(),
			subQuest.getActionUnitType().getUnit(),
			subQuest.getActionUnitNum(),
			attributes,
			subQuest.getDescription()
		);
	}

	/**
	 * 모든 서브 퀘스트의 로그를 수집합니다.
	 */
	private List<UsersSubQuestLog> collectAllSubQuestLogs(List<UsersSubQuest> subQuests) {
		List<UsersSubQuestLog> allLogs = new ArrayList<>();

		for (UsersSubQuest subQuest : subQuests) {
			List<UsersSubQuestLog> logs = usersSubQuestLogRepository.findByUsersSubQuestId(subQuest.getId());
			log.debug("서브 퀘스트 {} 로그 수: {}", subQuest.getId(), logs.size());
			allLogs.addAll(logs);
		}

		log.debug("전체 수집된 로그 수: {}", allLogs.size());
		return allLogs;
	}

	/**
	 * 로그를 날짜별로 그룹화하여 히스토리 DTO를 생성합니다.
	 */
	private List<QuestHistoryByDateDto> groupLogsByDate(List<UsersSubQuestLog> logs) {
		Map<LocalDate, List<UsersSubQuestLog>> groupedLogs = logs.stream()
			.collect(Collectors.groupingBy(log -> log.getCreatedAt().toLocalDate()));

		return groupedLogs.entrySet().stream()
			.map(this::convertToHistoryDto)
			.sorted(Comparator.comparing(QuestHistoryByDateDto::date).reversed())
			.collect(Collectors.toList());
	}

	/**
	 * 날짜별 로그 그룹을 히스토리 DTO로 변환합니다.
	 */
	private QuestHistoryByDateDto convertToHistoryDto(Map.Entry<LocalDate, List<UsersSubQuestLog>> entry) {
		LocalDate date = entry.getKey();
		List<UsersSubQuestLog> dailyLogs = entry.getValue();

		List<QuestHistoryByDateDto.SubQuestLogsResponseDto> dailyHistoryLogs = dailyLogs.stream()
			.map(this::convertToSubQuestLogResponseDto)
			.collect(Collectors.toList());

		return new QuestHistoryByDateDto(date, dailyHistoryLogs);
	}

	/**
	 * 서브 퀘스트 로그를 응답 DTO로 변환합니다.
	 */
	private QuestHistoryByDateDto.SubQuestLogsResponseDto convertToSubQuestLogResponseDto(UsersSubQuestLog log) {
		UsersSubQuest subQuest = log.getUsersSubQuest();
		SubQuestResponseDto.UsersSubQuestResponseDto subQuestDto = convertToUsersSubQuestResponseDto(subQuest);
		SubQuestLogDto logDto = new SubQuestLogDto(log.getId(), log.getDifficulty(), log.getMemo(), log.getImageUrls());

		return new QuestHistoryByDateDto.SubQuestLogsResponseDto(subQuestDto, logDto);
	}

	/**
	 * 서브 퀘스트 완료 로그를 생성합니다.
	 */
	private void createSubQuestLog(UsersSubQuest subQuest, SubQuestLogDto logDto) {
		UsersSubQuestLog subQuestLog = UsersSubQuestLog.builder()
			.usersSubQuest(subQuest)
			.difficulty(logDto.difficulty())
			.memo(logDto.memo())
			.imageUrls(logDto.imageUrls() != null ? logDto.imageUrls() : new ArrayList<>())
			.build();

		usersSubQuestLogRepository.save(subQuestLog);
		log.debug("서브 퀘스트 로그 생성 완료 - subQuestId: {}", subQuest.getId());

	}

	/**
	 * 서브 퀘스트 보상을 지급합니다.
	 */
	private List<AttributeDto> grantSubQuestRewards(UsersSubQuest subQuest) {
		List<AttributeDto> rewards = AttributeDto.fromUsersSubQuest(subQuest);
		attributeService.addExp(subQuest.getUsers(), rewards, subQuest);
		log.debug("서브 퀘스트 보상 지급 완료 - subQuestId: {}, rewards: {}", subQuest.getId(), rewards.size());
		return rewards;
	}

	/**
	 * 서브 퀘스트 로그를 수정합니다.
	 */
	private void updateSubQuestLog(UsersSubQuestLog log, SubQuestLogDto dto) {
		if (dto.difficulty() != null) {
			log.setDifficulty(dto.difficulty());
		}
		if (dto.memo() != null) {
			log.setMemo(dto.memo());
		}
		if (dto.imageUrls() != null) {
			log.setImageUrls(dto.imageUrls());
		}
	}

	// ==================== Quest Progress Calculation ====================

	/**
	 * 퀘스트 진행률 정보를 계산합니다.
	 */
	private QuestProgressInfo calculateQuestProgressInfo(UsersSubQuest subQuest) {
		LocalDate today = LocalDate.now();
		FrequencyType frequencyType = subQuest.getFrequencyType();

		log.debug("퀘스트 진행률 계산 시작 - subQuestId: {}, frequencyType: {}, today: {}",
			subQuest.getId(), frequencyType, today);

		return switch (frequencyType) {
			case DAILY -> calculateDailyProgress(subQuest, today);
			case WEEKLY_1, WEEKLY_2, WEEKLY_3, WEEKLY_4, WEEKLY_5, WEEKLY_6 ->
				calculateWeeklyProgress(subQuest, today);
			case MONTHLY_1, MONTHLY_2, MONTHLY_3, MONTHLY_4 ->
				calculateMonthlyProgress(subQuest, today);
			default -> new QuestProgressInfo(0, false);
		};
	}

	/**
	 * 일간 퀘스트 진행률을 계산합니다.
	 */
	private QuestProgressInfo calculateDailyProgress(UsersSubQuest subQuest, LocalDate today) {
		int todayLogCount = usersSubQuestLogRepository.countByUsersSubQuestIdAndCreatedAtAfter(
			subQuest.getId(), today.atStartOfDay());

		int remainingCount = FrequencyType.DAILY.getPer() - todayLogCount;
		boolean isEssential = todayLogCount == 0;

		log.debug("일간 퀘스트 진행률 - 오늘 완료 수: {}, 남은 수: {}, 필수 여부: {}",
			todayLogCount, remainingCount, isEssential);

		return new QuestProgressInfo(remainingCount, isEssential);
	}

	/**
	 * 주간 퀘스트 진행률을 계산합니다.
	 */
	private QuestProgressInfo calculateWeeklyProgress(UsersSubQuest subQuest, LocalDate today) {
		WeekPeriodInfo weekInfo = calculateCurrentWeekPeriod(subQuest, today);

		int weeklyLogCount = usersSubQuestLogRepository.countByUsersSubQuestIdAndCreatedAtAfter(
			subQuest.getId(), weekInfo.startDate().atStartOfDay());

		int requiredCount = subQuest.getFrequencyType().getCnt();
		int remainingCount = requiredCount - weeklyLogCount;
		long daysUntilWeekEnd = ChronoUnit.DAYS.between(today, weekInfo.endDate()) + 1;
		boolean isEssential = daysUntilWeekEnd <= remainingCount;

		log.debug("주간 퀘스트 진행률 - 주차: {} ~ {}, 완료 수: {}/{}, 남은 일수: {}, 필수 여부: {}",
			weekInfo.startDate(), weekInfo.endDate(), weeklyLogCount, requiredCount,
			daysUntilWeekEnd, isEssential);

		return new QuestProgressInfo(remainingCount, isEssential);
	}

	/**
	 * 월간 퀘스트 진행률을 계산합니다.
	 */
	private QuestProgressInfo calculateMonthlyProgress(UsersSubQuest subQuest, LocalDate today) {
		LocalDate startDate = subQuest.getMainQuest().getStartDate();
		LocalDate endDate = subQuest.getMainQuest().getEndDate();

		int totalLogCount = usersSubQuestLogRepository.countByUsersSubQuestIdAndCreatedAtAfter(
			subQuest.getId(), startDate.atStartOfDay());

		int requiredCount = subQuest.getFrequencyType().getCnt();
		int remainingCount = requiredCount - totalLogCount;
		long daysUntilEnd = ChronoUnit.DAYS.between(today, endDate) + 1;
		boolean isEssential = daysUntilEnd <= remainingCount;

		log.debug("월간 퀘스트 진행률 - 기간: {} ~ {}, 완료 수: {}/{}, 남은 일수: {}, 필수 여부: {}",
			startDate, endDate, totalLogCount, requiredCount, daysUntilEnd, isEssential);

		return new QuestProgressInfo(remainingCount, isEssential);
	}

	/**
	 * 현재 주차의 시작일과 종료일을 계산합니다.
	 */
	private WeekPeriodInfo calculateCurrentWeekPeriod(UsersSubQuest subQuest, LocalDate today) {
		LocalDate mainQuestStartDate = subQuest.getMainQuest().getStartDate();
		long daysSinceStart = ChronoUnit.DAYS.between(mainQuestStartDate, today);
		long weekOffset = (daysSinceStart / 7) * 7;

		LocalDate weekStartDate = mainQuestStartDate.plusDays(weekOffset);
		LocalDate weekEndDate = weekStartDate.plusDays(6);

		return new WeekPeriodInfo(weekStartDate, weekEndDate);
	}

	// ==================== Quest Status Management ====================

	/**
	 * 서브 퀘스트 완료 상태를 업데이트합니다.
	 */
	private void updateSubQuestStatus(UsersSubQuest subQuest) {
		FrequencyType type = subQuest.getFrequencyType();

		QuestStatus newStatus = switch (type) {
			case WEEKLY_1, WEEKLY_2, WEEKLY_3, WEEKLY_4, WEEKLY_5, WEEKLY_6 ->
				calculateWeeklyQuestStatus(subQuest, type);
			default -> QuestStatus.ACCOMPLISHED;
		};

		subQuest.setStatus(newStatus);
		log.debug("서브 퀘스트 상태 업데이트 - subQuestId: {}, newStatus: {}", subQuest.getId(), newStatus);
	}

	/**
	 * 주간 퀘스트의 완료 상태를 계산합니다.
	 */
	private QuestStatus calculateWeeklyQuestStatus(UsersSubQuest subQuest, FrequencyType type) {
		WeekPeriodInfo weekInfo = calculateCurrentWeekPeriod(subQuest, LocalDate.now());

		List<UsersSubQuestLog> logs = usersSubQuestLogRepository.findByUsersSubQuestId(subQuest.getId());
		long weeklyLogCount = logs.stream()
			.filter(log -> !log.getCreatedAt().isBefore(weekInfo.startDate().atStartOfDay()))
			.count();

		boolean weeklyGoalAchieved = weeklyLogCount >= type.getCnt();

		log.debug("주간 퀘스트 상태 계산 - 목표: {}, 완료: {}, 달성 여부: {}",
			type.getCnt(), weeklyLogCount, weeklyGoalAchieved);

		return weeklyGoalAchieved ? QuestStatus.WEEKLY_ACCOMPLISHED : QuestStatus.ACCOMPLISHED;
	}

	// ==================== Main Quest Completion ====================

	/**
	 * 메인 퀘스트 완료 여부를 확인하고 보상을 지급합니다.
	 */
	private MainQuestCompletionResult checkAndCompleteMainQuest(UsersMainQuest mainQuest) {
		log.info("메인 퀘스트 완료 검증 시작 - mainQuestId: {}, 제목: {}, 기간: {} ~ {}",
			mainQuest.getId(), mainQuest.getTitle(), mainQuest.getStartDate(), mainQuest.getEndDate());

		boolean allCompleted = validateAllSubQuestsCompleted(mainQuest);

		if (allCompleted) {
			return completeMainQuest(mainQuest);
		} else {
			log.info("❌ 메인 퀘스트 완료 실패 - mainQuestId: {}", mainQuest.getId());
			return new MainQuestCompletionResult(false, List.of());
		}
	}

	/**
	 * 모든 서브 퀘스트의 완료 여부를 검증합니다.
	 */
	private boolean validateAllSubQuestsCompleted(UsersMainQuest mainQuest) {
		for (UsersSubQuest subQuest : mainQuest.getUsersSubQuests()) {
			if (!isSubQuestCompleted(subQuest, mainQuest)) {
				log.info("서브 퀘스트 미완료 - subQuestId: {}, 설명: {}",
					subQuest.getId(), subQuest.getDescription());
				return false;
			}
			subQuest.setStatus(QuestStatus.COMPLETED);
		}
		return true;
	}

	/**
	 * 개별 서브 퀘스트의 완료 여부를 확인합니다.
	 */
	private boolean isSubQuestCompleted(UsersSubQuest subQuest, UsersMainQuest mainQuest) {
		List<UsersSubQuestLog> logs = usersSubQuestLogRepository.findByUsersSubQuestId(subQuest.getId());
		FrequencyType type = subQuest.getFrequencyType();

		log.debug("서브 퀘스트 완료 검증 - subQuestId: {}, 타입: {}, 로그 수: {}",
			subQuest.getId(), type, logs.size());

		return switch (type) {
			case DAILY -> validateDailyQuestCompletion(logs, mainQuest);
			case WEEKLY_1, WEEKLY_2, WEEKLY_3, WEEKLY_4, WEEKLY_5, WEEKLY_6 ->
				validateWeeklyQuestCompletion(subQuest, logs, mainQuest);
			case MONTHLY_1, MONTHLY_2, MONTHLY_3, MONTHLY_4 ->
				validateMonthlyQuestCompletion(logs, type);
			default -> false;
		};
	}

	/**
	 * 일간 퀘스트 완료 여부를 검증합니다.
	 */
	private boolean validateDailyQuestCompletion(List<UsersSubQuestLog> logs, UsersMainQuest mainQuest) {
		long requiredDays = ChronoUnit.DAYS.between(mainQuest.getStartDate(), mainQuest.getEndDate()) + 1;
		boolean completed = logs.size() >= requiredDays;

		log.debug("[DAILY] 필수 일수: {}, 완료 일수: {}, 완료 여부: {}",
			requiredDays, logs.size(), completed);

		return completed;
	}

	/**
	 * 주간 퀘스트 완료 여부를 검증합니다.
	 */
	private boolean validateWeeklyQuestCompletion(UsersSubQuest subQuest, List<UsersSubQuestLog> logs, UsersMainQuest mainQuest) {
		if (subQuest.getStatus() != QuestStatus.WEEKLY_ACCOMPLISHED) {
			return false;
		}

		int requiredPerWeek = subQuest.getFrequencyType().getCnt();
		LocalDate currentDate = mainQuest.getStartDate();
		LocalDate endDate = mainQuest.getEndDate();

		while (!currentDate.isAfter(endDate)) {
			LocalDate weekEnd = currentDate.plusDays(6);
			if (weekEnd.isAfter(endDate)) {
				weekEnd = endDate;
			}

			if (!validateWeekPeriod(logs, currentDate, weekEnd, requiredPerWeek)) {
				return false;
			}

			currentDate = currentDate.plusWeeks(1);
		}

		return true;
	}

	/**
	 * 특정 주차의 완료 조건을 검증합니다.
	 */
	private boolean validateWeekPeriod(List<UsersSubQuestLog> logs, LocalDate startDate, LocalDate endDate, int required) {
		long logsInPeriod = logs.stream()
			.filter(log -> {
				LocalDate logDate = log.getCreatedAt().toLocalDate();
				return !logDate.isBefore(startDate) && !logDate.isAfter(endDate);
			})
			.count();

		boolean periodCompleted = logsInPeriod >= required;

		log.debug("[WEEKLY] 기간: {} ~ {}, 필수: {}, 완료: {}, 결과: {}",
			startDate, endDate, required, logsInPeriod, periodCompleted);

		return periodCompleted;
	}

	/**
	 * 월간 퀘스트 완료 여부를 검증합니다.
	 */
	private boolean validateMonthlyQuestCompletion(List<UsersSubQuestLog> logs, FrequencyType type) {
		int required = type.getCnt();
		boolean completed = logs.size() >= required;

		log.debug("[MONTHLY] 필수 수행: {}, 완료 수행: {}, 완료 여부: {}",
			required, logs.size(), completed);

		return completed;
	}

	/**
	 * 메인 퀘스트를 완료 처리하고 보상을 지급합니다.
	 */
	private MainQuestCompletionResult completeMainQuest(UsersMainQuest mainQuest) {
		List<AttributeDto> mainQuestRewards = AttributeDto.fromUsersMainQuest(mainQuest);

		mainQuest.setStatus(QuestStatus.COMPLETED);
		attributeService.addExp(mainQuest.getUsers(), mainQuestRewards, mainQuest);

		log.info("✔ 메인 퀘스트 완료 처리 완료 - mainQuestId: {}, 보상 수: {}",
			mainQuest.getId(), mainQuestRewards.size());

		return new MainQuestCompletionResult(true, mainQuestRewards);
	}

	// ==================== Inner Classes ====================

	/**
	 * 퀘스트 진행률 정보를 담는 클래스
	 */
	private record QuestProgressInfo(
		int repeatCount,
		boolean essential
	) {

	}

	/**
	 * 주차 기간 정보를 담는 클래스
	 */
	private record WeekPeriodInfo(
		LocalDate startDate,
		LocalDate endDate
	) {

	}

	/**
	 * 메인 퀘스트 완료 결과를 담는 클래스
	 */
	private record MainQuestCompletionResult(
		boolean completed,
		List<AttributeDto> rewards
	) {

	}
}