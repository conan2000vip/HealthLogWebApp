package com.healthlog.app.service.feedbackservice;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.healthlog.app.entity.Sleep;
import com.healthlog.app.repository.SleepRepository;
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;
import com.healthlog.app.service.feedbackservice.model.FeedbackLevel;
import com.healthlog.app.service.feedbackservice.model.FeedbackType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SleepFeedbackRule {

	private final SleepRepository sleepRepository;

	private static final int LV3_LOOKBACK_DAYS = 3;
	private static final int SHORT_SLEEP_MINUTES_THRESHOLD = 5 * 60; // 5時間（即時判定・3日平均で共通使用）

	private static final int LV4_CONSECUTIVE_DAYS = 5;
	private static final int LV4_DAILY_MINUTES_THRESHOLD = 6 * 60; // 6時間

	public List<FeedbackItem> evaluate(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();

		LocalDate today = LocalDate.now();
		LocalDate from = today.minusDays(LV4_CONSECUTIVE_DAYS + 2L);
		List<Sleep> logs = sleepRepository
				.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId, from);
		if (logs.isEmpty()) {
			return items;
		}

		Map<LocalDate, Integer> dailyTotals = computeDailyTotals(logs);

		checkContinuousShortSleep(dailyTotals, today, items);
		checkShortAverageSleep(dailyTotals, today, items);

		return items;
	}

	// ---- Lv4: 直近5日連続で睡眠時間が6時間未満 ----
	private void checkContinuousShortSleep(
			Map<LocalDate, Integer> dailyTotals,
			LocalDate today,
			List<FeedbackItem> items) {
		for (int i = 0; i < LV4_CONSECUTIVE_DAYS; i++) {
			LocalDate date = today.minusDays(i);
			Integer minutes = dailyTotals.get(date);
			if (minutes == null || minutes >= LV4_DAILY_MINUTES_THRESHOLD) {
				return;
			}
		}
		items.add(new FeedbackItem(
				FeedbackType.SLEEP_CONTINUOUS_SHORT,
				FeedbackLevel.LV4,
				"睡眠不足が続いています",
				LV4_CONSECUTIVE_DAYS + "日連続で6時間未満の睡眠です。十分な休息を取ることをおすすめします。",
				today.atStartOfDay(),
				"alert-octagon"));
	}

	// ---- Lv3: 直近3日間の平均睡眠時間が5時間未満 ----
	// データが3日分揃っていない場合は、当日単独の即時判定にフォールバックする
	private void checkShortAverageSleep(
			Map<LocalDate, Integer> dailyTotals,
			LocalDate today,
			List<FeedbackItem> items) {
		List<Integer> recent = new ArrayList<>();
		for (int i = 0; i < LV3_LOOKBACK_DAYS; i++) {
			LocalDate date = today.minusDays(i);
			Integer minutes = dailyTotals.get(date);
			if (minutes != null) {
				recent.add(minutes);
			}
		}

		if (recent.size() < LV3_LOOKBACK_DAYS) {
			// データ不足のため3日平均は判定できない → 当日のみの即時フィードバックにフォールバック
			checkTodayShortSleep(dailyTotals, today, items);
			return;
		}

		double average = recent.stream().mapToInt(Integer::intValue).average().orElse(0);
		if (average < SHORT_SLEEP_MINUTES_THRESHOLD) {
			items.add(new FeedbackItem(
					FeedbackType.SLEEP_SHORT,
					FeedbackLevel.LV3,
					"睡眠時間が短い日が続いています",
					"直近" + LV3_LOOKBACK_DAYS + "日間の平均睡眠時間が5時間未満です。休息時間を見直しましょう。",
					today.atStartOfDay(),
					"alert-triangle"));
		}
	}

	// ---- Lv2: 当日の睡眠時間が5時間未満（即時判定・データ不足時のフォールバック） ----
	private void checkTodayShortSleep(
			Map<LocalDate, Integer> dailyTotals,
			LocalDate today,
			List<FeedbackItem> items) {
		Integer minutesToday = dailyTotals.get(today);
		if (minutesToday == null || minutesToday >= SHORT_SLEEP_MINUTES_THRESHOLD) {
			return;
		}
		int hours = minutesToday / 60;
		int mins = minutesToday % 60;
		items.add(new FeedbackItem(
				FeedbackType.SLEEP_SHORT,
				FeedbackLevel.LV2,
				"今日の睡眠時間が短いです",
				"今日の睡眠は" + hours + "時間" + mins + "分でした。十分な休息を心がけましょう。",
				today.atStartOfDay(),
				"lightbulb"));
	}

	private Map<LocalDate, Integer> computeDailyTotals(List<Sleep> logs) {
		Map<LocalDate, Integer> map = new HashMap<>();
		for (Sleep s : logs) {
			int minutes = s.getSleepMinutes() == null ? 0 : s.getSleepMinutes();
			map.merge(s.getRecordedDate(), minutes, Integer::sum);
		}
		return map;
	}
}