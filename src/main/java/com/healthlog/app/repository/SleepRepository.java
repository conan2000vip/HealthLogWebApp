package com.healthlog.app.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Sleep;

@Repository
public interface SleepRepository extends JpaRepository<Sleep, Long> {

	// idx_sleep_profile_date — 日別睡眠履歴検索
	List<Sleep> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(
			Long profileId, LocalDate startDate, LocalDate endDate);

	List<Sleep> findByProfile_IdAndRecordedDate(Long profileId, LocalDate recordedDate);

}