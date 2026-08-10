package com.healthlog.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import com.healthlog.app.entity.Step;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.repository.ProfileRepository;
import com.healthlog.app.repository.StepRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class StepService {

	private final StepRepository stepRepository;
	private final ProfileRepository profileRepository;

	private record ChartData(List<String> labels, List<Integer> values) {
	}

	// ---------------------------------------------------------
	// Common: Get profile and check ownership / 共通：Profile取得・所有者確認
	// ---------------------------------------------------------
	private Profile findProfile(Long profileId, Long currentUserId) {
		Profile profile = profileRepository.findById(profileId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "データが見つかりません"));
		if (!profile.getUser().getId().equals(currentUserId)) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "アクセス権限がありません");
		}
		return profile;
	}

	// Common: Get step record / 共通：Step取得
	private Step findStep(Long logId) {
		return stepRepository.findById(logId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "データが見つかりません"));
	}

	// Common: Check record ownership / 共通：記録のProfile確認
	private void validateProfileOwner(Step log, Long profileId) {
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

		boolean hasAnyLog = stepRepository.existsByProfile_Id(profileId);
		Integer goal = profile.getStepGoal(); // NULLの場合は目標未設定

		// ---- Latest record / 最新記録 ----
		Optional<Step> latestOpt = stepRepository.findTopByProfile_IdOrderByRecordedDateDesc(profileId);

		Integer latest = latestOpt.map(Step::getSteps).orElse(null);
		LocalDate latestDate = latestOpt.map(Step::getRecordedDate).orElse(null);
		LocalDateTime latestUpdatedAt = latestOpt.map(Step::getUpdatedAt).orElse(null);

		// ---- Today's steps and goal rate / 今日の歩数・目標達成率 ----
		LocalDate today = LocalDate.now();
		Optional<Step> todayOpt = stepRepository.findFirstByProfile_IdAndRecordedDate(profileId, today);
		Integer todaySteps = todayOpt.map(Step::getSteps).orElse(null);
		Integer goalRatePercent = (todaySteps != null && goal != null && goal > 0)
				? (int) Math.round(todaySteps * 100.0 / goal)
				: null;

		// ---- Monthly average / 今月平均 ----
		LocalDate firstDay = today.withDayOfMonth(1);
		LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
		List<Step> monthLogs = fetchLogs(profileId, firstDay, lastDay);
		Integer monthAverage = monthLogs.isEmpty() ? null
				: (int) Math.round(monthLogs.stream().map(Step::getSteps).filter(steps -> steps != null)
						.mapToInt(Integer::intValue).average().orElse(0));

		// ---- Current page / 現在ページ ----
		Pageable pageable = PageRequest.of(page, 20);
		Page<Step> logPage = fetchLogsPage(profileId, from, to, pageable);
		List<Step> pageLogs = logPage.getContent();
		for (Step step : pageLogs) {
			step.setGoalRate(calculateGoalRate(step.getSteps(), goal));
		}

		// ---- Chart data / グラフデータ ----
		List<Step> allLogs = fetchLogs(profileId, from, to);
		String chartMode = determineChartMode(from, to);

		ChartData chartData = buildChartData(allLogs, chartMode, from, to);

		Map<String, Object> stats = new HashMap<>();
		stats.put("latest", latest);
		stats.put("latestDate", latestDate);
		stats.put("latestUpdatedAt", latestUpdatedAt);
		stats.put("todaySteps", todaySteps);
		stats.put("todayDate", today);
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
		result.put("labels", chartData.labels());
		result.put("values", chartData.values());
		result.put("chartMode", chartMode);
		result.put("chartFrom", from);
		result.put("chartTo", to);
		return result;
	}

	// Goal rate / 目標達成率
	private BigDecimal calculateGoalRate(Integer steps, Integer goal) {
		if (steps == null || goal == null || goal <= 0) {
			return null;
		}
		return BigDecimal.valueOf(steps).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(goal), 0,
				RoundingMode.HALF_UP);
	}

	// ---------------------------------------------------------
	// create()
	// ---------------------------------------------------------
	public Step create(Long profileId, Long currentUserId, Step input) {
		Profile profile = findProfile(profileId, currentUserId);
		validateStepInput(input);
		Optional<Step> existingOpt = stepRepository.findFirstByProfile_IdAndRecordedDate(profileId,
				input.getRecordedDate());
		Step step;
		if (existingOpt.isPresent()) {
			// 同じ日付のデータが存在する場合は上書き
			step = existingOpt.get();
			step.setSteps(input.getSteps());
			step.setMemo(input.getMemo() != null ? input.getMemo().trim() : null);

		} else {
			// 新しい日付の場合は新規登録
			step = new Step();
			step.setProfile(profile);
			step.setRecordedDate(input.getRecordedDate());
			step.setSteps(input.getSteps());
			step.setMemo(input.getMemo() != null ? input.getMemo().trim() : null);
		}
		return stepRepository.save(step);
	}

	// ---------------------------------------------------------
	// update()
	// ---------------------------------------------------------
	public Step update(Long profileId, Long currentUserId, Long logId, Step input) {
		findProfile(profileId, currentUserId);
		Step log = findStep(logId);
		validateProfileOwner(log, profileId);
		validateStepInput(input);

		Optional<Step> sameDate = stepRepository.findFirstByProfile_IdAndRecordedDate(profileId,
				input.getRecordedDate());
		if (sameDate.isPresent() && !sameDate.get().getId().equals(logId)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "この日にはすでに歩数データが登録されています");
		}

		log.setRecordedDate(input.getRecordedDate());
		log.setSteps(input.getSteps());
		log.setMemo(input.getMemo() != null ? input.getMemo().trim() : null);
		return stepRepository.save(log);
	}

	// ---------------------------------------------------------
	// delete()
	// ---------------------------------------------------------
	public void delete(Long profileId, Long currentUserId, Long logId) {
		findProfile(profileId, currentUserId);
		Step log = findStep(logId);
		validateProfileOwner(log, profileId);
		stepRepository.delete(log);
	}

	// ---------------------------------------------------------
	// fetchLogs()
	// ---------------------------------------------------------
	private List<Step> fetchLogs(Long profileId, LocalDate from, LocalDate to) {
		if (from != null && to != null) {
			return stepRepository.findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(profileId, from, to);
		}
		if (from != null) {
			return stepRepository.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId,
					from);
		}
		if (to != null) {
			return stepRepository.findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(profileId, to);
		}
		return stepRepository.findByProfile_IdOrderByRecordedDateDesc(profileId);
	}

	// ---------------------------------------------------------
	// fetchLogsPage()
	// ---------------------------------------------------------
	private Page<Step> fetchLogsPage(Long profileId, LocalDate from, LocalDate to, Pageable pageable) {
		if (from != null && to != null) {
			return stepRepository.findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(profileId, from, to,
					pageable);
		}
		if (from != null) {
			return stepRepository.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId,
					from, pageable);
		}
		if (to != null) {
			return stepRepository.findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(profileId, to,
					pageable);
		}
		return stepRepository.findByProfile_IdOrderByRecordedDateDesc(profileId, pageable);
	}

	// ---------------------------------------------------------
	// Chart data / グラフデータ
	// ---------------------------------------------------------
	// ★注意: Stepは日付ごとに1件のみ（時刻フィールドが無い）ため、HOURモードは提供しない
	private String determineChartMode(LocalDate from, LocalDate to) {
		if (from == null || to == null)
			return "DAY";
		long days = ChronoUnit.DAYS.between(from, to) + 1;
		if (days <= 7)
			return "DAY";
		if (days <= 31)
			return "WEEK";
		if (days <= 1095)
			return "MONTH";
		return "YEAR";
	}

	public Map<String, Object> chartData(Long profileId, Long currentUserId, LocalDate from, LocalDate to) {
		findProfile(profileId, currentUserId);
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "開始日が終了日より後になっているため、期間指定が正しくありません。");
		}
		String chartMode = determineChartMode(from, to);
		List<Step> logs = fetchLogs(profileId, from, to);
		ChartData chartData = buildChartData(logs, chartMode, from, to);
		Map<String, Object> result = new HashMap<>();
		result.put("labels", chartData.labels());
		result.put("values", chartData.values());
		result.put("chartMode", chartMode);
		return result;
	}

	private ChartData buildChartData(List<Step> logs, String chartMode, LocalDate from, LocalDate to) {
		List<String> labels = new ArrayList<>();
		List<Integer> values = new ArrayList<>();

		if ("DAY".equals(chartMode)) {
			Map<LocalDate, Integer> daily = logs.stream().filter(step -> step.getRecordedDate() != null)
					.collect(Collectors.toMap(Step::getRecordedDate,
							step -> step.getSteps() == null ? 0 : step.getSteps(), (oldValue, newValue) -> newValue,
							TreeMap::new));

			if (from == null || to == null) {
				daily.forEach((date, steps) -> {
					labels.add(date.toString());
					values.add(steps);
				});
			} else {
				LocalDate current = from;
				while (!current.isAfter(to)) {
					labels.add(current.toString());
					values.add(daily.get(current));
					current = current.plusDays(1);
				}
			}
		} else if ("WEEK".equals(chartMode)) {
			Map<LocalDate, Integer> daily = logs.stream().filter(step -> step.getRecordedDate() != null)
					.collect(Collectors.toMap(Step::getRecordedDate,
							step -> step.getSteps() == null ? 0 : step.getSteps(), (oldValue, newValue) -> newValue,
							TreeMap::new));
			LocalDate current = from;
			while (!current.isAfter(to)) {
				LocalDate weekEnd = current.plusDays(6).isAfter(to) ? to : current.plusDays(6);
				labels.add(current.toString());
				List<Integer> weekValues = new ArrayList<>();
				LocalDate d = current;
				while (!d.isAfter(weekEnd)) {
					Integer v = daily.get(d);
					if (v != null)
						weekValues.add(v);
					d = d.plusDays(1);
				}
				values.add(weekValues.isEmpty() ? null
						: (int) Math.round(weekValues.stream().mapToInt(Integer::intValue).average().orElse(0)));
				current = current.plusDays(7);
			}
		} else if ("MONTH".equals(chartMode)) {
			Map<String, List<Step>> monthly = logs.stream()
					.collect(Collectors.groupingBy(
							step -> step.getRecordedDate().getYear() + "-"
									+ String.format("%02d", step.getRecordedDate().getMonthValue()),
							TreeMap::new, Collectors.toList()));

			LocalDate current = from.withDayOfMonth(1);
			while (!current.isAfter(to)) {
				String monthKey = current.getYear() + "-" + String.format("%02d", current.getMonthValue());
				labels.add(monthKey);
				List<Step> list = monthly.get(monthKey);
				if (list == null || list.isEmpty()) {
					values.add(null);
				} else {
					int average = (int) Math.round(list.stream().map(Step::getSteps).filter(steps -> steps != null)
							.mapToInt(Integer::intValue).average().orElse(0));
					values.add(average);
				}
				current = current.plusMonths(1);
			}
		} else if ("YEAR".equals(chartMode)) {
			Map<Integer, List<Step>> yearly = logs.stream().collect(
					Collectors.groupingBy(step -> step.getRecordedDate().getYear(), TreeMap::new, Collectors.toList()));

			LocalDate current = from.withDayOfYear(1);
			while (!current.isAfter(to)) {
				int year = current.getYear();
				labels.add(String.valueOf(year));
				List<Step> list = yearly.get(year);
				if (list == null || list.isEmpty()) {
					values.add(null);
				} else {
					int average = (int) Math.round(list.stream().map(Step::getSteps).filter(steps -> steps != null)
							.mapToInt(Integer::intValue).average().orElse(0));
					values.add(average);
				}
				current = current.plusYears(1);
			}
		}
		return new ChartData(labels, values);
	}

	// ---------------------------------------------------------
	// Input validation / 入力チェック
	// ---------------------------------------------------------
	private void validateStepInput(Step step) {
		if (step.getRecordedDate() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "記録日を入力してください");
		}
		if (step.getRecordedDate().isAfter(LocalDate.now())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "未来の日付は指定できません");
		}
		if (step.getSteps() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "歩数を入力してください");
		}
		if (step.getSteps() < 0 || step.getSteps() > 100000) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "歩数は0〜100000歩の範囲で入力してください");
		}
		if (step.getMemo() != null && step.getMemo().length() > 500) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "メモは500文字以内で入力してください");
		}
	}
}