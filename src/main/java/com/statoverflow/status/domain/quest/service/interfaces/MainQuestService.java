package com.statoverflow.status.domain.quest.service.interfaces;

import java.util.List;

import com.statoverflow.status.domain.quest.dto.response.MainQuestResponseDto;

public interface MainQuestService {

	List<MainQuestResponseDto> getMainQuests(Long userId, Long theme);

	List<MainQuestResponseDto> rerollMainQuests(List<Long> mainQuests, Long userId, Long theme);

}
