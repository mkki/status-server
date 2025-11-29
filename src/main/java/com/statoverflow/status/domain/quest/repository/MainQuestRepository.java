package com.statoverflow.status.domain.quest.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.statoverflow.status.domain.master.entity.MainQuest;

public interface MainQuestRepository extends JpaRepository<MainQuest, Long> {
	@Query("SELECT mq FROM MainQuest mq WHERE mq.theme.id = :themeId AND (BITAND(mq.linkedAttribute, :selectedAttributes) = :selectedAttributes)")
	List<MainQuest> findAllByThemeIdAndAttributes(Long themeId, int selectedAttributes);

	List<MainQuest> findAllByThemeId(Long themeId);
}
