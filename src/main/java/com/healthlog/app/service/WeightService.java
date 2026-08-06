package com.healthlog.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.Weight;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.repository.ProfileRepository;
import com.healthlog.app.repository.WeightRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class WeightService {

	private final WeightRepository weightRepository;
	private final ProfileRepository profileRepository;

	// 子供は記録時の身長、成人はprofileの身長を使う
	private BigDecimal resolveHeight(Profile profile, Weight weight) {
		if (weight.getHeight() != null) {
			return weight.getHeight();
		}
		return profile.getHeight();
	}

	// ---------------------------------------------------------
	// 共通: Profile取得 + 所有者チェック
	// profileId が存在しない場合は404、存在するが currentUserId のものでない場合は403
	// ---------------------------------------------------------
	private Profile findProfile(Long profileId, Long currentUserId) {
		Profile profile = profileRepository.findById(profileId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "データが見つかりません"));
		if (!profile.getUser().getId().equals(currentUserId)) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "アクセス権限がありません");
		}
		return profile;
	}

	// 共通: Weight取得
	private Weight findWeight(Long logId) {
		return weightRepository.findById(logId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "データが見つかりません"));
	}

	// 共通: Weightログが指定profileのものであるかチェック
	private void validateProfileOwner(Weight log, Long profileId) {
		if (!log.getProfile().getId().equals(profileId)) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "アクセス権限がありません");
		}
	}

	// 同日に複数回計測した場合、最新の記録（id最大）のみ残す
	private List<Weight> latestPerDate(List<Weight> logs) {
		Map<LocalDate, Weight> map = new LinkedHashMap<>();
		for (Weight w : logs) {
			Weight existing = map.get(w.getRecordedDate());
			if (existing == null || w.getId() > existing.getId()) {
				map.put(w.getRecordedDate(), w);
			}
		}
		return map.values().stream().sorted(Comparator.comparing(Weight::getRecordedDate)).collect(Collectors.toList());
	}

	private Map<String, Object> buildChartData(List<Weight> logs, String chartMode, LocalDate from, LocalDate to) {
		List<String> labels = new ArrayList<>();
		List<BigDecimal> values = new ArrayList<>();
		if ("DAY".equals(chartMode)) {
			Map<LocalDate, Weight> daily = new TreeMap<>();
			for (Weight w : latestPerDate(logs)) {
				daily.put(w.getRecordedDate(), w);
			}
			if (from == null || to == null) {
				daily.forEach((date, weight) -> {
					labels.add(date.toString());
					values.add(weight.getWeight());
				});
			} else {
				LocalDate current = from;
				while (!current.isAfter(to)) {
					labels.add(current.toString());
					Weight w = daily.get(current);
					values.add(w == null ? null : w.getWeight());
					current = current.plusDays(1);
				}
			}
		} else if ("MONTH".equals(chartMode)) {
			Map<String, List<Weight>> monthly = latestPerDate(logs).stream()
					.collect(Collectors.groupingBy(w -> w.getRecordedDate().getYear() + "-"
							+ String.format("%02d", w.getRecordedDate().getMonthValue()), TreeMap::new,
							Collectors.toList()));
			LocalDate current = from.withDayOfMonth(1);
			while (!current.isAfter(to)) {
				String monthKey = current.getYear() + "-" + String.format("%02d", current.getMonthValue());
				labels.add(monthKey);
				List<Weight> list = monthly.get(monthKey);
				if (list == null || list.isEmpty()) {
					values.add(null);
				} else {
					BigDecimal avg = list.stream().map(Weight::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add)
							.divide(BigDecimal.valueOf(list.size()), 1, RoundingMode.HALF_UP);
					values.add(avg);
				}
				current = current.plusMonths(1);
			}
		} else if ("YEAR".equals(chartMode)) {
			Map<Integer, List<Weight>> yearly = latestPerDate(logs).stream()
					.collect(Collectors.groupingBy(w -> w.getRecordedDate().getYear(), TreeMap::new,
							Collectors.toList()));
			LocalDate current = from.withDayOfYear(1);
			while (!current.isAfter(to)) {
				int year = current.getYear();
				labels.add(String.valueOf(year));
				List<Weight> list = yearly.get(year);
				if (list == null || list.isEmpty()) {
					values.add(null);
				} else {
					BigDecimal avg = list.stream().map(Weight::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add)
							.divide(BigDecimal.valueOf(list.size()), 1, RoundingMode.HALF_UP);
					values.add(avg);
				}
				current = current.plusYears(1);
			}
		}
		Map<String, Object> result = new HashMap<>();
		result.put("labels", labels);
		result.put("values", values);
		return result;
	}

	// ---------------------------------------------------------
	// list()
	// ---------------------------------------------------------
	public Map<String, Object> list(Long profileId, Long currentUserId, LocalDate from, LocalDate to, int page) {
		Profile profile = findProfile(profileId, currentUserId);
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "開始日が終了日より後になっているため、期間指定が正しくありません。");
		}

		// ---- 1) 全件（ページングなし）: 最新/最小/最大/BMI + グラフ用 ----
		List<Weight> allLogs = fetchLogs(profileId, from, to);
		boolean hasAnyLog = weightRepository.existsByProfile_Id(profileId);

		Weight minLog = allLogs.stream().min(Comparator.comparing(Weight::getWeight)).orElse(null);
		Weight maxLog = allLogs.stream().max(Comparator.comparing(Weight::getWeight)).orElse(null);
		BigDecimal min = minLog != null ? minLog.getWeight() : null;
		BigDecimal max = maxLog != null ? maxLog.getWeight() : null;

		// 最新の記録は1回だけ計算して latest と BMI の両方に使い回す
		Weight latestLog = allLogs.stream()
				.max(Comparator.comparing(Weight::getRecordedDate).thenComparing(Weight::getId))
				.orElse(null);
		BigDecimal latest = latestLog != null ? latestLog.getWeight() : null;

		BigDecimal bmi = null;
		String[] overallStatus = new String[] { null, null };
		if (latestLog != null) {
			BigDecimal h = resolveHeight(profile, latestLog);
			bmi = calculateBMI(latestLog.getWeight(), h);
			overallStatus = bmiStatusOf(bmi);
		}

		String chartMode = "DAY";
		if (from != null && to != null) {
			long days = ChronoUnit.DAYS.between(from, to) + 1;
			if (days <= 31) {
				// 1か月以内 → 日別
				chartMode = "DAY";
			} else if (days <= 1095) {
				// 3年以内 → 月別
				chartMode = "MONTH";
			} else {
				// 3年以上 → 年別
				chartMode = "YEAR";
			}
		}

		Map<String, Object> chartData = buildChartData(allLogs, chartMode, from, to);
		List<String> labels = (List<String>) chartData.get("labels");
		List<BigDecimal> values = (List<BigDecimal>) chartData.get("values");

		// ---- 2) 現在ページのみ: テーブル表示用 ----
		Pageable pageable = PageRequest.of(page, 20);
		Page<Weight> logPage = fetchLogsPage(profileId, from, to, pageable);
		List<Weight> pageLogs = logPage.getContent();
		for (Weight w : pageLogs) {
			BigDecimal h = resolveHeight(profile, w);
			BigDecimal rowBmi = calculateBMI(w.getWeight(), h);
			w.setBmi(rowBmi);
			String[] status = bmiStatusOf(rowBmi);
			w.setBmiStatus(status[0]);
			w.setBmiStatusCode(status[1]);
		}

		Map<String, Object> stats = new HashMap<>();
		stats.put("latest", latest);
		stats.put("latestDate", latestLog != null ? latestLog.getRecordedDate() : null);
		stats.put("min", min);
		stats.put("max", max);
		stats.put("bmi", bmi);
		stats.put("bmiStatus", overallStatus[0]);
		stats.put("bmiStatusCode", overallStatus[1]);
		stats.put("latestDate", latestLog != null ? latestLog.getRecordedDate() : null);
		stats.put("minDate", minLog != null ? minLog.getRecordedDate() : null);
		stats.put("maxDate", maxLog != null ? maxLog.getRecordedDate() : null);

		Map<String, Object> result = new HashMap<>();
		result.put("currentProfile", profile);
		result.put("logs", pageLogs);
		result.put("stats", stats);
		result.put("labels", labels);
		result.put("values", values);
		result.put("hasAnyLog", hasAnyLog);
		result.put("currentPage", page);
		result.put("totalPages", logPage.getTotalPages());
		result.put("hasNext", logPage.hasNext());
		result.put("hasPrevious", logPage.hasPrevious());
		result.put("chartMode", chartMode);

		return result;
	}

	private String[] bmiStatusOf(BigDecimal bmi) {
		if (bmi == null)
			return new String[] { null, null };
		if (bmi.compareTo(BigDecimal.valueOf(18.5)) < 0)
			return new String[] { "低体重", "low" };
		if (bmi.compareTo(BigDecimal.valueOf(25)) < 0)
			return new String[] { "標準", "normal" };
		if (bmi.compareTo(BigDecimal.valueOf(30)) < 0)
			return new String[] { "肥満(1度)", "warning" };
		return new String[] { "肥満(2度以上)", "high" };
	}

	// ---------------------------------------------------------
	// create()
	// ---------------------------------------------------------
	public Weight create(Long profileId, Long currentUserId, Weight weight) {
		Profile profile = findProfile(profileId, currentUserId);
		validateWeightInput(weight);
		if (weight.getHeight() == null) {
			weight.setHeight(profile.getHeight());
		}
		weight.setProfile(profile);
		return weightRepository.save(weight);
	}

	// ---------------------------------------------------------
	// update()
	// ---------------------------------------------------------
	public Weight update(Long profileId, Long currentUserId, Long logId, Weight input) {
		findProfile(profileId, currentUserId); // profileId が currentUserId のものであるか確認
		Weight log = findWeight(logId);
		validateProfileOwner(log, profileId); // logId が profileId のものであるか確認
		validateWeightInput(input);
		if (input.getHeight() == null) {
			input.setHeight(log.getProfile().getHeight());
		}
		log.setRecordedDate(input.getRecordedDate());
		log.setMeasuredAt(input.getMeasuredAt());
		log.setWeight(input.getWeight());
		log.setHeight(input.getHeight());
		log.setMemo(input.getMemo());
		return weightRepository.save(log);
	}

	// ---------------------------------------------------------
	// delete()
	// ---------------------------------------------------------
	public void delete(Long profileId, Long currentUserId, Long logId) {
		findProfile(profileId, currentUserId);
		Weight log = findWeight(logId);
		validateProfileOwner(log, profileId);
		weightRepository.delete(log);
	}

	// ---------------------------------------------------------
	// fetchLogs()共通: from/toのnullを考慮したログ取得（list/chart共通化）
	// ---------------------------------------------------------
	private List<Weight> fetchLogs(Long profileId, LocalDate from, LocalDate to) {
		if (from != null && to != null) {
			return weightRepository.findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(
					profileId, from, to);
		}
		if (from != null) {
			return weightRepository.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(
					profileId, from);
		}
		if (to != null) {
			return weightRepository.findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(
					profileId, to);
		}
		return weightRepository.findByProfile_IdOrderByRecordedDateDesc(profileId);
	}

	// fetchLogsPage()
	private Page<Weight> fetchLogsPage(Long profileId, LocalDate from, LocalDate to, Pageable pageable) {
		if (from != null && to != null) {
			return weightRepository.findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(profileId, from, to,
					pageable);
		}
		if (from != null) {
			return weightRepository.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId,
					from, pageable);
		}
		if (to != null) {
			return weightRepository.findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(profileId, to,
					pageable);
		}
		return weightRepository.findByProfile_IdOrderByRecordedDateDesc(profileId, pageable);
	}

	// ---------------------------------------------------------
	// 共通: create/updateの入力チェック
	// ---------------------------------------------------------
	private void validateWeightInput(Weight w) {
		if (w.getRecordedDate() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "記録日を入力してください");
		}
		if (w.getRecordedDate().isAfter(LocalDate.now())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "未来の日付は指定できません");
		}
		if (w.getWeight() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "体重を入力してください");
		}
		if (w.getWeight().compareTo(BigDecimal.ONE) < 0 ||
				w.getWeight().compareTo(BigDecimal.valueOf(500)) > 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "体重は1〜500kgの範囲で入力してください");
		}
		if (w.getHeight() != null) {
			if (w.getHeight().compareTo(BigDecimal.valueOf(30)) < 0 ||
					w.getHeight().compareTo(BigDecimal.valueOf(250)) > 0) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, "身長は30〜250cmの範囲で入力してください");
			}
		}
		if (w.getMemo() != null && w.getMemo().length() > 500) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "500文字以内で入力してください");
		}
	}

	// ---------------------------------------------------------
	// BMI計算
	// ---------------------------------------------------------
	private BigDecimal calculateBMI(BigDecimal weight, BigDecimal height) {
		if (weight == null || height == null || height.compareTo(BigDecimal.ZERO) <= 0) {
			return null;
		}
		BigDecimal heightMeter = height.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
		return weight.divide(heightMeter.multiply(heightMeter), 2, RoundingMode.HALF_UP);
	}
}