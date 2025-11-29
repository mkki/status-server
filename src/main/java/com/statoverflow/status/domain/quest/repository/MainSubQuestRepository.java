package com.statoverflow.status.domain.quest.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.statoverflow.status.domain.master.entity.MainSubQuest;
import com.statoverflow.status.domain.master.entity.MainSubQuestId;

@Repository
public interface MainSubQuestRepository extends JpaRepository<MainSubQuest, MainSubQuestId> {

	@Query(value = "SELECT * FROM main_sub_quest msq " +
		"WHERE msq.main_quest_id = :mainQuestId " +
		"AND (msq.linked_attribute & :selectedAttributes) = :selectedAttributes",
		nativeQuery = true)
	List<MainSubQuest> findAllByMainQuestIdAndAttributes(
		@Param("mainQuestId") Long mainQuestId,
		@Param("selectedAttributes") Integer selectedAttributes
	);

	MainSubQuest findByMainQuestIdAndSubQuestId(Long id, Long id1);

	List<MainSubQuest> findAllByMainQuestId(Long mainQuestId);
}
