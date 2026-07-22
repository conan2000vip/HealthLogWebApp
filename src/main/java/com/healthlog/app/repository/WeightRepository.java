package com.healthlog.app.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Weight;

@Repository
public interface WeightRepository extends JpaRepository<Weight, Long> {

	boolean existsByProfile_Id(Long profileId);

	List<Weight> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(Long profileId, LocalDate startDate,
			LocalDate endDate);

	List<Weight> findByProfile_IdOrderByRecordedDateDesc(Long profileId);

	List<Weight> findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(Long profileId, LocalDate from);

	List<Weight> findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(Long profileId, LocalDate to);

	Page<Weight> findByProfile_IdOrderByRecordedDateDesc(Long profileId, Pageable pageable);

	Page<Weight> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(
			Long profileId, LocalDate from, LocalDate to, Pageable pageable);

	Page<Weight> findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(
			Long profileId, LocalDate from, Pageable pageable);

	Page<Weight> findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(
			Long profileId, LocalDate to, Pageable pageable);

	Optional<Weight> findTopByProfile_IdOrderByRecordedDateDesc(Long profileId);

}