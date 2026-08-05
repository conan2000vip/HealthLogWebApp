package com.healthlog.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.Water;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.repository.ProfileRepository;
import com.healthlog.app.repository.WaterRepository;
import com.healthlog.app.service.feedbackservice.WaterFeedbackRule;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class WaterService {

	private final WaterRepository waterRepository;
	private final ProfileRepository profileRepository;

	private final WaterFeedbackRule waterFeedbackRule;

	// ---------------------------------------------------------
	// 共通: Profile取得 + 所有者チェック
	// ---------------------------------------------------------
	private Profile findProfile(Long profileId, Long currentUserId) {
		Profile profile = profileRepository.findById(profileId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "データが見つかりません"));
		if (!profile.getUser().getId().equals(currentUserId)) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "アクセス権限がありません");
		}
		return profile;
	}

	private Water findWater(Long logId) {
		return waterRepository.findById(logId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "データが見つかりません"));
	}

	private void validateProfileOwner(Water log, Long profileId) {
		if (!log.getProfile().getId().equals(profileId)) {
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

		boolean hasAnyLog = waterRepository.existsByProfile_Id(profileId);
		Integer goal = profile.getWaterGoalMl(); // カラムがNOT NULLのため常に非null

		// ---- 最新（全期間で最新の1件） ----
		Optional<Water> latestOpt = waterRepository.findTopByProfile_IdOrderByRecordedDateDescIdDesc(profileId);
		Integer latest = latestOpt.map(Water::getAmountMl).orElse(null);

		// ---- 今日合計・目標達成率 ----
		LocalDate today = LocalDate.now();
		List<Water> todayLogs = waterRepository.findByProfile_IdAndRecordedDate(profileId, today);
		Integer todayTotal = todayLogs.isEmpty() ? null
				: todayLogs.stream().mapToInt(w -> w.getAmountMl() == null ? 0 : w.getAmountMl()).sum();
		Integer goalRatePercent = (todayTotal != null && goal != null && goal > 0)
				? (int) Math.round(todayTotal * 100.0 / goal)
				: null;

		// ---- 今月平均（1日単位で算出、フィルターの影響を受けない固定値） ----
		LocalDate firstDay = today.withDayOfMonth(1);
		LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
		List<Water> monthLogs = fetchLogs(profileId, firstDay, lastDay);
		Map<LocalDate, Integer> dailyTotalsMonth = computeDailyTotals(monthLogs);
		Integer monthAverage = dailyTotalsMonth.isEmpty() ? null
				: (int) Math.round(dailyTotalsMonth.values().stream().mapToInt(Integer::intValue).average().orElse(0));

		// ---- 現在ページ分: テーブル表示用（1件ごとの目標比を計算） ----
		Pageable pageable = PageRequest.of(page, 20);
		Page<Water> logPage = fetchLogsPage(profileId, from, to, pageable);
		List<Water> pageLogs = logPage.getContent();
		for (Water w : pageLogs) {
			w.setGoalRate(calculateGoalRate(w.getAmountMl(), goal));
		}

		// ---- グラフ用データ（日付ごとの合計摂取量） ----
		List<Water> allLogs = fetchLogs(profileId, from, to);
		Map<String, Object> chartData = buildChartData(allLogs);

		Map<String, Object> stats = new HashMap<>();
		stats.put("latest", latest);
		stats.put("todayTotal", todayTotal);
		stats.put("monthAverage", monthAverage);
		stats.put("goalRate", goalRatePercent);

		Map<String, Object> result = new HashMap<>();
		result.put("currentProfile", profile);
		result.put("logs", pageLogs);
		result.put("stats", stats);
		result.put("hasAnyLog", hasAnyLog);
		result.put("currentPage", page);
		result.put("totalPages", logPage.getTotalPages());
		result.put("hasNext", logPage.hasNext());
		result.put("hasPrevious", logPage.hasPrevious());
		result.put("labels", chartData.get("labels"));
		result.put("values", chartData.get("values"));
		return result;
	}

	// 1件ごとの、その日の目標に対する割合(%)
	private BigDecimal calculateGoalRate(Integer amountMl, Integer goal) {
		if (amountMl == null || goal == null || goal <= 0) {
			return null;
		}
		return BigDecimal.valueOf(amountMl)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(goal), 0, RoundingMode.HALF_UP);
	}

	// ---------------------------------------------------------
	// create()
	// ---------------------------------------------------------
	public Water create(Long profileId, Long currentUserId, Water water) {
		Profile profile = findProfile(profileId, currentUserId);
		validateWaterInput(water);
		water.setProfile(profile);
		return waterRepository.save(water);
	}

	// ---------------------------------------------------------
	// update()
	// ---------------------------------------------------------
	public Water update(Long profileId, Long currentUserId, Long logId, Water input) {
		findProfile(profileId, currentUserId);
		Water log = findWater(logId);
		validateProfileOwner(log, profileId);
		validateWaterInput(input);

		log.setRecordedDate(input.getRecordedDate());
		log.setRecordedTime(input.getRecordedTime());
		log.setDrinkType(input.getDrinkType());
		log.setAmountMl(input.getAmountMl());
		log.setMemo(input.getMemo());
		return waterRepository.save(log);
	}

	// ---------------------------------------------------------
	// delete()
	// ---------------------------------------------------------
	public void delete(Long profileId, Long currentUserId, Long logId) {
		findProfile(profileId, currentUserId);
		Water log = findWater(logId);
		validateProfileOwner(log, profileId);
		waterRepository.delete(log);
	}

	// ---------------------------------------------------------
	// fetchLogs() / fetchLogsPage()
	// ---------------------------------------------------------
	private List<Water> fetchLogs(Long profileId, LocalDate from, LocalDate to) {
		if (from != null && to != null) {
			return waterRepository.findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(profileId, from, to);
		}
		if (from != null) {
			return waterRepository.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId,
					from);
		}
		if (to != null) {
			return waterRepository.findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(profileId, to);
		}
		return waterRepository.findByProfile_IdOrderByRecordedDateDesc(profileId);
	}

	private Page<Water> fetchLogsPage(Long profileId, LocalDate from, LocalDate to, Pageable pageable) {
		if (from != null && to != null) {
			return waterRepository.findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(profileId, from, to,
					pageable);
		}
		if (from != null) {
			return waterRepository.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId,
					from, pageable);
		}
		if (to != null) {
			return waterRepository.findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(profileId, to,
					pageable);
		}
		return waterRepository.findByProfile_IdOrderByRecordedDateDesc(profileId, pageable);
	}

	// ---------------------------------------------------------
	// computeDailyTotals() / buildChartData()
	// ---------------------------------------------------------
	private Map<LocalDate, Integer> computeDailyTotals(List<Water> logs) {
		return logs.stream()
				.collect(Collectors.groupingBy(Water::getRecordedDate, TreeMap::new,
						Collectors.summingInt(w -> w.getAmountMl() == null ? 0 : w.getAmountMl())));
	}

	private Map<String, Object> buildChartData(List<Water> logs) {
		Map<LocalDate, Integer> daily = computeDailyTotals(logs);
		List<String> labels = new ArrayList<>();
		List<Integer> values = new ArrayList<>();
		daily.forEach((date, total) -> {
			labels.add(date.toString());
			values.add(total);
		});
		Map<String, Object> result = new HashMap<>();
		result.put("labels", labels);
		result.put("values", values);
		return result;
	}

	// ---------------------------------------------------------
	// 共通: create/updateの入力チェック
	// ---------------------------------------------------------
	private void validateWaterInput(Water w) {
		if (w.getRecordedDate() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "日付を入力してください");
		}
		if (w.getRecordedDate().isAfter(LocalDate.now())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "未来の日付は指定できません");
		}
		if (w.getDrinkType() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "飲み物の種類を選択してください");
		}
		if (w.getAmountMl() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "水分量を入力してください");
		}
		if (w.getAmountMl() < 1 || w.getAmountMl() > 5000) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "水分量は1〜5000mlの範囲で入力してください");
		}
		if (w.getMemo() != null && w.getMemo().length() > 500) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "メモは500文字以内で入力してください");
		}
	}
}