package com.healthlog.app.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Memo;

@Repository
public interface MemoRepository extends JpaRepository<Memo, Long> {
	boolean existsByProfile_Id(Long profileId);

	List<Memo> findByProfile_IdAndRecordedDateOrderByIdDesc(Long profileId, LocalDate recordedDate);

	List<Memo> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDescIdDesc(Long profileId, LocalDate from,
			LocalDate to);

	Page<Memo> findByProfile_IdOrderByRecordedDateDescIdDesc(Long profileId, Pageable pageable);

	Page<Memo> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDescIdDesc(Long profileId, LocalDate from,
			LocalDate to, Pageable pageable);

	Page<Memo> findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDescIdDesc(Long profileId,
			LocalDate from, Pageable pageable);

	Page<Memo> findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDescIdDesc(Long profileId, LocalDate to,
			Pageable pageable);

	long countByProfile_IdAndRecordedDate(Long profileId, LocalDate recordedDate);
}