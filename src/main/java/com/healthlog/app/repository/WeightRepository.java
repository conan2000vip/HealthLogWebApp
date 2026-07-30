package com.healthlog.app.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Weight;

@Repository
public interface WeightRepository extends JpaRepository<Weight, Long> {

	// idx_weight_profile_date — 期間検索・一覧表示
	List<Weight> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(
			Long profileId, LocalDate startDate, LocalDate endDate);

	List<Weight> findByProfile_IdOrderByRecordedDateDesc(Long profileId);

	Optional<Weight> findTopByProfile_IdOrderByRecordedDateDesc(Long profileId);

}