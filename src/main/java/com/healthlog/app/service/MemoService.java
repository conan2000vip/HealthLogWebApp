package com.healthlog.app.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healthlog.app.entity.Memo;
import com.healthlog.app.entity.Profile;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.repository.MemoRepository;
import com.healthlog.app.repository.ProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MemoService {

	private final MemoRepository memoRepository;
	private final ProfileRepository profileRepository;

	// Common: Get profile and check ownership / 共通：Profile取得・所有者確認
	private Profile findProfile(Long profileId, Long currentUserId) {
		Profile profile = profileRepository.findById(profileId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "データが見つかりません"));

		if (!profile.getUser().getId().equals(currentUserId)) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "アクセス権限がありません");
		}
		return profile;
	}

	// Common: Get memo / 共通：Memo取得
	private Memo findMemo(Long logId) {
		return memoRepository.findById(logId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "データが見つかりません"));
	}

	// Common: Check record ownership / 共通：記録のProfile確認
	private void validateProfileOwner(Memo memo, Long profileId) {
		if (!memo.getProfile().getId().equals(profileId)) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "アクセス権限がありません");
		}
	}

	// ---------------------------------------------------------
	// list()
	// ---------------------------------------------------------
	public Map<String, Object> list(Long profileId, Long currentUserId, LocalDate from, LocalDate to, int page) {
		Profile profile = findProfile(profileId, currentUserId);

		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "開始日が終了日より後になっているため、期間指定が正しくありません。");
		}

		boolean hasAnyLog = memoRepository.existsByProfile_Id(profileId);
		Pageable pageable = PageRequest.of(Math.max(page, 0), 20);
		Page<Memo> logPage = fetchLogsPage(profileId, from, to, pageable);

		Map<String, Object> result = new HashMap<>();
		result.put("currentProfile", profile);
		result.put("logs", logPage.getContent());
		result.put("hasAnyLog", hasAnyLog);
		result.put("currentPage", logPage.getNumber());
		result.put("totalPages", logPage.getTotalPages());
		result.put("hasNext", logPage.hasNext());
		result.put("hasPrevious", logPage.hasPrevious());
		return result;
	}

	// Recent three days / 直近3日間のメモ
	public List<Memo> getRecentThreeDays(Long profileId, Long currentUserId) {
		findProfile(profileId, currentUserId);

		LocalDate to = LocalDate.now();
		LocalDate from = to.minusDays(2);

		return memoRepository.findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDescIdDesc(profileId, from, to);
	}

	// ---------------------------------------------------------
	// Get records by date / 指定日の記録取得
	// ---------------------------------------------------------
	public List<Memo> getByDate(Long profileId, Long currentUserId, LocalDate recordedDate) {
		findProfile(profileId, currentUserId);

		if (recordedDate == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "記録日を指定してください");
		}

		return memoRepository.findByProfile_IdAndRecordedDateOrderByIdDesc(profileId, recordedDate);
	}

	// ---------------------------------------------------------
	// create()
	// ---------------------------------------------------------
	public Memo create(Long profileId, Long currentUserId, Memo memo) {
		Profile profile = findProfile(profileId, currentUserId);

		normalizeInput(memo);
		validateMemoInput(memo);

		memo.setProfile(profile);
		return memoRepository.save(memo);
	}

	// ---------------------------------------------------------
	// update()
	// ---------------------------------------------------------
	public Memo update(Long profileId, Long currentUserId, Long logId, Memo input) {
		findProfile(profileId, currentUserId);

		Memo memo = findMemo(logId);
		validateProfileOwner(memo, profileId);

		normalizeInput(input);
		validateMemoInput(input);

		memo.setRecordedDate(input.getRecordedDate());
		memo.setTitle(input.getTitle());
		memo.setContent(input.getContent());

		return memoRepository.save(memo);
	}

	// ---------------------------------------------------------
	// delete()
	// ---------------------------------------------------------
	public void delete(Long profileId, Long currentUserId, Long logId) {
		findProfile(profileId, currentUserId);

		Memo memo = findMemo(logId);
		validateProfileOwner(memo, profileId);

		memoRepository.delete(memo);
	}

	// ---------------------------------------------------------
	// Fetch records with pagination / ページング付き記録取得
	// ---------------------------------------------------------
	private Page<Memo> fetchLogsPage(Long profileId, LocalDate from, LocalDate to, Pageable pageable) {
		if (from != null && to != null) {
			return memoRepository.findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDescIdDesc(profileId, from, to, pageable);
		}
		if (from != null) {
			return memoRepository.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDescIdDesc(profileId, from, pageable);
		}
		if (to != null) {
			return memoRepository.findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDescIdDesc(profileId, to, pageable);
		}
		return memoRepository.findByProfile_IdOrderByRecordedDateDescIdDesc(profileId, pageable);
	}

	// ---------------------------------------------------------
	// Input normalization / 入力値整形
	// ---------------------------------------------------------
	private void normalizeInput(Memo memo) {
		if (memo.getTitle() != null) {
			String title = memo.getTitle().trim();
			memo.setTitle(title.isEmpty() ? null : title);
		}
		if (memo.getContent() != null) {
			memo.setContent(memo.getContent().trim());
		}
	}

	// ---------------------------------------------------------
	// Input validation / 入力チェック
	// ---------------------------------------------------------
	private void validateMemoInput(Memo memo) {
		if (memo.getRecordedDate() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "記録日を入力してください");
		}
		if (memo.getRecordedDate().isAfter(LocalDate.now())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "未来の日付は指定できません");
		}
		if (memo.getTitle() != null && memo.getTitle().length() > 100) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "タイトルは100文字以内で入力してください");
		}
		if (memo.getContent() == null || memo.getContent().isBlank()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "メモ内容を入力してください");
		}
		if (memo.getContent().length() > 2000) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "メモ内容は2000文字以内で入力してください");
		}
	}
}