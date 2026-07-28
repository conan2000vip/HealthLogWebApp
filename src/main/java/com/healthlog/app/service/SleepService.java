package com.healthlog.app.service;

import java.time.LocalDate;
import java.time.LocalTime;
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
import com.healthlog.app.entity.Sleep;
import com.healthlog.app.entity.Sleep.SleepType;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.repository.ProfileRepository;
import com.healthlog.app.repository.SleepRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SleepService {
	private final SleepRepository sleepRepository;
	private final ProfileRepository profileRepository;

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

	// 共通: Sleep取得
	private Sleep findSleep(Long logId) {
		return sleepRepository.findById(logId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "データが見つかりません"));
	}

	// 共通: Sleepログが指定profileのものであるかチェック
	private void validateProfileOwner(Sleep log, Long profileId) {
		if (!log.getProfile().getId().equals(profileId)) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "アクセス権限がありません");
		}
	}

	// ---------------------------------------------------------
	// list()
	// ---------------------------------------------------------
	public Map<String, Object> list(Long profileId, Long currentUserId, LocalDate from, LocalDate to, int page) {
		// FIX: trước đây findProfile() bị gọi lặp lại 2 lần liên tiếp
		// (dòng thứ 2 dư thừa, gây thêm 1 query DB không cần thiết) -> đã xóa
		Profile profile = findProfile(profileId, currentUserId);
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "開始日が終了日より後になっているため、期間指定が正しくありません。");
		}

		// 今月の統計（未フィルタ時のデフォルト範囲）
		LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
		LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
		boolean hasAnyLog = sleepRepository.existsByProfile_Id(profileId);

		// FIX: nếu người dùng có lọc theo from/to, thống kê phải tính theo
		// đúng khoảng đó thay vì luôn cố định theo tháng hiện tại.
		// Nếu không lọc gì (from/to đều null) -> mặc định dùng tháng hiện tại.
		LocalDate statsFrom = (from == null && to == null) ? firstDay : from;
		LocalDate statsTo = (from == null && to == null) ? lastDay : to;
		List<Sleep> statsLogs = fetchLogs(profileId, statsFrom, statsTo);

		// 最新（フィルタに関係なく、全期間の最新記録）
		Sleep latestLog = sleepRepository.findTopByProfile_IdOrderByRecordedDateDesc(profileId).orElse(null);
		Integer latest = latestLog != null ? latestLog.getSleepMinutes() : null;

		// FIX: 同じ日に昼寝(NAP)と夜間睡眠(NIGHT)を両方記録した場合、
		// 従来は record 単位（昼寝レコード・夜間レコードを別々の値）として
		// 平均/最短/最長を計算していたため、昼寝の短い時間に引っ張られて
		// 数値がおかしくなっていた。
		// -> まず日付ごとに合算(昼寝+夜間)してから、その「1日分の合計」を
		//    単位として平均/最短/最長を計算するように修正。
		Map<LocalDate, Integer> dailyTotals = computeDailyTotals(statsLogs);
		List<Integer> dailyValues = new ArrayList<>(dailyTotals.values());

		// 平均（選択期間 or 今月、1日単位で算出）
		Integer periodAverage = dailyValues.isEmpty() ? null
				: (int) Math.round(dailyValues.stream().mapToInt(Integer::intValue).average().orElse(0));

		// 最短（選択期間 or 今月、1日単位で算出）
		Integer shortest = dailyValues.stream().mapToInt(Integer::intValue).min().orElse(0);

		// 最長（選択期間 or 今月、1日単位で算出）
		Integer longest = dailyValues.stream().mapToInt(Integer::intValue).max().orElse(0);
		Pageable pageable = PageRequest.of(page, 20);
		Page<Sleep> logPage = fetchLogsPage(profileId, from, to, pageable);
		List<Sleep> pageLogs = logPage.getContent();

		// Stats
		Map<String, Object> stats = new HashMap<>();
		stats.put("latest", latest);
		stats.put("monthlyAverage", periodAverage);
		stats.put("shortest", shortest);
		stats.put("longest", longest);
		// フラグ: 現在表示中の統計が「今月固定」か「検索期間ベース」かをテンプレート側で判定できるように
		stats.put("isCustomRange", !(from == null && to == null));

		// グラフ用データ（日付ごとの合計睡眠時間）
		List<Sleep> allLogs = fetchLogs(profileId, from, to);
		Map<String, Object> chartData = buildChartData(allLogs);

		// Result
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

	// ---------------------------------------------------------
	// computeDailyTotals()共通: 日付ごとに睡眠時間(分)を合算する
	// （同じ日の昼寝(NAP)＋夜間睡眠(NIGHT)は合算して1日分として扱う）
	// buildChartData() と stats（平均/最短/最長）の両方で使用
	// ---------------------------------------------------------
	private Map<LocalDate, Integer> computeDailyTotals(List<Sleep> logs) {
		return logs.stream()
				.collect(Collectors.groupingBy(Sleep::getRecordedDate, TreeMap::new,
						Collectors.summingInt(s -> s.getSleepMinutes() == null ? 0 : s.getSleepMinutes())));
	}

	// ---------------------------------------------------------
	// buildChartData()共通: 日付ごとに睡眠時間(分)を合計してグラフ用データを作る
	// （list/chart 共通化）
	// ---------------------------------------------------------
	private Map<String, Object> buildChartData(List<Sleep> logs) {
		Map<LocalDate, Integer> dailySleep = computeDailyTotals(logs);
		List<String> labels = new ArrayList<>();
		List<Integer> values = new ArrayList<>();
		dailySleep.forEach((date, minutes) -> {
			// FIX: trước đây dùng format "M/d" (VD: "7/28"), không đồng nhất với
			// WeightService (dùng LocalDate.toString() -> "2026-07-28").
			// Vì frontend (formatShortDate trong sleep.js/weight.js) parse theo
			// chuẩn ISO "yyyy-MM-dd" để rút gọn hiển thị, format "M/d" khiến hàm
			// đó không hoạt động đúng và hiển thị style ngày khác nhau giữa 2 trang.
			labels.add(date.toString());
			values.add(minutes);
		});
		Map<String, Object> result = new HashMap<>();
		result.put("labels", labels);
		result.put("values", values);
		return result;
	}

	// ---------------------------------------------------------
	// create()
	// ---------------------------------------------------------
	public Sleep create(Long profileId, Long currentUserId, Sleep sleep) {
		Profile profile = findProfile(profileId, currentUserId);
		validateSleepInput(sleep);
		Optional<Sleep> existing = sleepRepository.findFirstByProfile_IdAndRecordedDateAndSleepType(
				profileId,
				sleep.getRecordedDate(),
				sleep.getSleepType());
		if (existing.isPresent()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, sleep.getSleepType().equals(SleepType.NIGHT)
					? "この日の夜間睡眠は既に登録されています"
					: "この日の昼寝は既に登録されています");
		}
		// 未選択の場合は夜間睡眠
		if (sleep.getSleepType() == null) {
			sleep.setSleepType(SleepType.NIGHT);
		}
		sleep.setProfile(profile);
		sleep.setSleepMinutes(calculateSleepMinutes(sleep.getStartTime(), sleep.getEndTime()));
		return sleepRepository.save(sleep);
	}

	// ---------------------------------------------------------
	// update()
	// ---------------------------------------------------------
	public Sleep update(Long profileId, Long currentUserId, Long logId, Sleep input) {
		findProfile(profileId, currentUserId); // profileId が currentUserId のものであるか確認
		Sleep log = findSleep(logId);
		validateProfileOwner(log, profileId); // logId が profileId のものであるか確認
		validateSleepInput(input);

		log.setRecordedDate(input.getRecordedDate());
		log.setStartTime(input.getStartTime());
		log.setEndTime(input.getEndTime());
		log.setSleepType(input.getSleepType() == null ? SleepType.NIGHT : input.getSleepType());
		if (input.getMemo() != null) {
			log.setMemo(input.getMemo().trim());
		} else {
			log.setMemo(null);
		}
		log.setSleepMinutes(calculateSleepMinutes(input.getStartTime(), input.getEndTime()));
		return sleepRepository.save(log);
	}

	// ---------------------------------------------------------
	// delete()
	// ---------------------------------------------------------
	public void delete(Long profileId, Long currentUserId, Long logId) {
		findProfile(profileId, currentUserId);
		Sleep log = findSleep(logId);
		validateProfileOwner(log, profileId);
		sleepRepository.delete(log);
	}

	// ---------------------------------------------------------
	// fetchLogs()
	// ---------------------------------------------------------
	private List<Sleep> fetchLogs(Long profileId, LocalDate from, LocalDate to) {
		if (from != null && to != null) {
			return sleepRepository
					.findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(profileId, from, to);
		}
		if (from != null) {
			return sleepRepository
					.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId, from);
		}
		if (to != null) {
			return sleepRepository
					.findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(profileId, to);
		}
		return sleepRepository.findByProfile_IdOrderByRecordedDateDesc(profileId);
	}

	// ---------------------------------------------------------
	// fetchLogsPage()
	// ---------------------------------------------------------
	private Page<Sleep> fetchLogsPage(Long profileId, LocalDate from, LocalDate to, Pageable pageable) {
		if (from != null && to != null) {
			return sleepRepository
					.findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(profileId, from, to, pageable);
		}
		if (from != null) {
			return sleepRepository
					.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId, from, pageable);
		}
		if (to != null) {
			return sleepRepository
					.findByProfile_IdAndRecordedDateLessThanEqualOrderByRecordedDateDesc(profileId, to, pageable);
		}
		return sleepRepository
				.findByProfile_IdOrderByRecordedDateDesc(profileId, pageable);
	}

	// ---------------------------------------------------------
	// 共通: create/updateの入力チェック
	// ---------------------------------------------------------
	private void validateSleepInput(Sleep sleep) {
		if (sleep.getRecordedDate() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "記録日を入力してください");
		}
		if (sleep.getRecordedDate().isAfter(LocalDate.now())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "未来の日付は指定できません");
		}
		if (sleep.getStartTime() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "就寝時刻を入力してください");
		}
		if (sleep.getEndTime() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "起床時刻を入力してください");
		}
		// 未選択の場合はデフォルトを設定
		if (sleep.getSleepType() == null) {
			sleep.setSleepType(SleepType.NIGHT);
		}
		if (sleep.getSleepType() != SleepType.NIGHT && sleep.getSleepType() != SleepType.NAP) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "睡眠種別が不正です");
		}
		if (sleep.getMemo() != null && sleep.getMemo().length() > 500) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "メモは500文字以内で入力してください");
		}
		Integer minutes = calculateSleepMinutes(sleep.getStartTime(), sleep.getEndTime());
		if (minutes <= 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "睡眠時間が不正です");
		}
		if (minutes > 16 * 60) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "睡眠時間が長すぎます");
		}
	}

	// ---------------------------------------------------------
	// 睡眠時間計算
	// ---------------------------------------------------------
	private Integer calculateSleepMinutes(LocalTime start, LocalTime end) {
		if (start == null || end == null) {
			return null;
		}
		int startMinute = start.getHour() * 60 + start.getMinute();
		int endMinute = end.getHour() * 60 + end.getMinute();
		// ngủ qua ngày hôm sau
		if (endMinute < startMinute) {
			endMinute += 24 * 60;
		}
		return endMinute - startMinute;
	}
}