package com.healthlog.app.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
		Profile profile = findProfile(profileId, currentUserId);
		findProfile(profileId, currentUserId);
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "検索期間が不正です");
		}

		// 今月の統計
		LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
		LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
		List<Sleep> monthLogs = sleepRepository
				.findByProfile_IdAndRecordedDateBetweenOrderByRecordedDateDesc(profileId, firstDay, lastDay);
		boolean hasAnyLog = sleepRepository.existsByProfile_Id(profileId);

		// 最新
		Sleep latestLog = sleepRepository.findTopByProfile_IdOrderByRecordedDateDesc(profileId).orElse(null);
		Integer latest = latestLog != null ? latestLog.getSleepMinutes() : null;

		// 今月平均
		Integer monthlyAverage = null;
		if (!monthLogs.isEmpty()) {
			monthlyAverage = (int) Math.round(monthLogs.stream()
					.filter(s -> s.getSleepMinutes() != null)
					.mapToInt(Sleep::getSleepMinutes)
					.average()
					.orElse(0));
		}

		// 最短
		Integer shortest = monthLogs.stream()
				.filter(s -> s.getSleepMinutes() != null)
				.mapToInt(Sleep::getSleepMinutes)
				.min()
				.orElse(0);

		// 最長
		Integer longest = monthLogs.stream()
				.filter(s -> s.getSleepMinutes() != null)
				.mapToInt(Sleep::getSleepMinutes)
				.max()
				.orElse(0);
		Pageable pageable = PageRequest.of(page, 20);
		Page<Sleep> logPage = fetchLogsPage(profileId, from, to, pageable);
		List<Sleep> pageLogs = logPage.getContent();

		// Stats
		Map<String, Object> stats = new HashMap<>();
		stats.put("latest", latest);
		stats.put("monthlyAverage", monthlyAverage);
		stats.put("shortest", shortest);
		stats.put("longest", longest);

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
	// buildChartData()共通: 日付ごとに睡眠時間(分)を合計してグラフ用データを作る
	// （list/chart 共通化）
	// ---------------------------------------------------------
	private Map<String, Object> buildChartData(List<Sleep> logs) {
		Map<LocalDate, Integer> dailySleep = logs.stream()
				.collect(Collectors.groupingBy(Sleep::getRecordedDate, TreeMap::new,
						Collectors.summingInt(s -> s.getSleepMinutes() == null ? 0 : s.getSleepMinutes())));
		List<String> labels = new ArrayList<>();
		List<Integer> values = new ArrayList<>();
		dailySleep.forEach((date, minutes) -> {
			labels.add(date.format(DateTimeFormatter.ofPattern("M/d")));
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
	// chart()
	// ---------------------------------------------------------
	public Map<String, Object> chart(Long profileId, Long currentUserId, LocalDate from, LocalDate to) {
		findProfile(profileId, currentUserId);
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "開始日は終了日より前の日付を選択してください");
		}
		List<Sleep> logs = fetchLogs(profileId, from, to);
		return buildChartData(logs);
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