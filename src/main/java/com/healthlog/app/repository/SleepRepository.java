package com.healthlog.app.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Sleep;
import com.healthlog.app.entity.Sleep.SleepType;

@Repository
public interface SleepRepository extends JpaRepository<Sleep, Long> {

	boolean existsByProfile_Id(Long profileId);

	// List

	List<Sleep> findByProfile_IdOrderByRecordedDateDesc(Long profileId);

	List<Sleep> findByProfile_IdAndRecordedDateOrderByStartTimeAsc(
			Long profileId,
			LocalDate recordedDate);

	List<Sleep> findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(
			Long profileId,
			LocalDate from);

	List<Sleep> findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(
			Long profileId,
			LocalDate to);

	List<Sleep> findByProfile_IdAndRecordedDateAndSleepTypeOrderByStartTimeAsc(
			Long profileId,
			LocalDate recordedDate,
			SleepType sleepType);

	List<Sleep> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(
			Long profileId,
			LocalDate startDate,
			LocalDate endDate);

	// Page

	Page<Sleep> findByProfile_IdOrderByRecordedDateDesc(
			Long profileId,
			Pageable pageable);

	Page<Sleep> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(
			Long profileId,
			LocalDate from,
			LocalDate to,
			Pageable pageable);

	Page<Sleep> findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(
			Long profileId,
			LocalDate from,
			Pageable pageable);

	Page<Sleep> findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(
			Long profileId,
			LocalDate to,
			Pageable pageable);

	// Latest

	Optional<Sleep> findTopByProfile_IdOrderByRecordedDateDesc(Long profileId);

	Optional<Sleep> findFirstByProfile_IdAndRecordedDateAndSleepType(
			Long profileId,
			LocalDate recordedDate,
			SleepType sleepType);

}