package com.healthlog.app.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Water;

@Repository
public interface WaterRepository extends JpaRepository<Water, Long> {

	// idx_water_profile_date — 日別水分履歴検索・集計
	List<Water> findByProfile_IdAndRecordedDate(Long profileId, LocalDate recordedDate);

	List<Water> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(
			Long profileId, LocalDate startDate, LocalDate endDate);

}