package com.healthlog.app.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Step;

@Repository
public interface StepRepository extends JpaRepository<Step, Long> {

	boolean existsByProfile_Id(Long profileId);

	boolean existsByProfile_IdAndRecordedDate(Long profileId, LocalDate recordedDate);

	Optional<Step> findTopByProfile_IdAndRecordedDateLessThanOrderByRecordedDateDesc(Long profileId, LocalDate date);

	Optional<Step> findTopByProfile_IdOrderByRecordedDateDescIdDesc(Long profileId);

	Optional<Step> findFirstByProfile_IdAndRecordedDate(Long profileId, LocalDate recordedDate);

	Optional<Step> findTopByProfile_IdOrderByRecordedDateDesc(Long profileId);

	List<Step> findByProfile_IdOrderByRecordedDateDesc(Long profileId);

	List<Step> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(Long profileId, LocalDate from,
			LocalDate to);

	List<Step> findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(Long profileId, LocalDate from);

	List<Step> findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(Long profileId, LocalDate to);

	Page<Step> findByProfile_IdOrderByRecordedDateDesc(Long profileId, Pageable pageable);

	Page<Step> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(Long profileId, LocalDate from,
			LocalDate to, Pageable pageable);

	Page<Step> findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(Long profileId, LocalDate from,
			Pageable pageable);

	Page<Step> findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(Long profileId, LocalDate to,
			Pageable pageable);

}