package com.healthlog.app.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Memo;

@Repository
public interface MemoRepository extends JpaRepository<Memo, Long> {

	// idx_memo_profile_date — 期間検索・一覧表示
	List<Memo> findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(
			Long profileId, LocalDate startDate, LocalDate endDate);

	List<Memo> findByProfile_IdOrderByRecordedDateDesc(Long profileId);

}