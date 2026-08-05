package com.healthlog.app.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Water;

@Repository
public interface WaterRepository extends JpaRepository<Water, Long> {

	boolean existsByProfile_Id(Long profileId);

	// idx_water_profile_date — 日別水分履歴検索・集計
	List<Water> findByProfile_IdAndRecordedDate(Long profileId, LocalDate recordedDate);

	List<Water> findByProfile_IdOrderByRecordedDateDesc(Long profileId);

	List<Water> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(
			Long profileId, LocalDate startDate, LocalDate endDate);

	List<Water> findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(
			Long profileId, LocalDate from);

	List<Water> findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(
			Long profileId, LocalDate to);

	Page<Water> findByProfile_IdOrderByRecordedDateDesc(Long profileId, Pageable pageable);

	Page<Water> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(
			Long profileId, LocalDate from, LocalDate to, Pageable pageable);

	Page<Water> findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(
			Long profileId, LocalDate from, Pageable pageable);

	Page<Water> findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(
			Long profileId, LocalDate to, Pageable pageable);

	// 全期間の最新1件（日付→id の順で最新を判定）
	Optional<Water> findTopByProfile_IdOrderByRecordedDateDescIdDesc(Long profileId);
}