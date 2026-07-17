package com.healthlog.app.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Step;

@Repository
public interface StepRepository extends JpaRepository<Step, Long> {

	// idx_step_profile_date — 1プロフィール1日1件
	Optional<Step> findByProfile_IdAndRecordedDate(Long profileId, LocalDate recordedDate);

	List<Step> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(
			Long profileId, LocalDate startDate, LocalDate endDate);

}