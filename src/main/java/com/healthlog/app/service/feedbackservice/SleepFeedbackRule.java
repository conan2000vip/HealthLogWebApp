package com.healthlog.app.service.feedbackservice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.Sleep;
import com.healthlog.app.repository.ProfileRepository;
import com.healthlog.app.repository.SleepRepository;
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;
import com.healthlog.app.service.feedbackservice.model.FeedbackLevel;
import com.healthlog.app.service.feedbackservice.model.FeedbackType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SleepFeedbackRule {

	private final SleepRepository sleepRepository;
	private final ProfileRepository profileRepository;

	private static final int LV3_LOOKBACK_DAYS = 3;
	private static final int SHORT_SLEEP_MINUTES_THRESHOLD = 5 * 60; // 5時間（即時判定・3日平均で共通使用）

	private static final int LV4_CONSECUTIVE_DAYS = 5;
	private static final int LV4_DAILY_MINUTES_THRESHOLD = 6 * 60; // 6時間

	public List<FeedbackItem> evaluate(Long profileId) {
		Profile profile = profileRepository.findById(profileId).orElseThrow();
		List<FeedbackItem> items = new ArrayList<>();
		LocalDate today = LocalDate.now();
		LocalDate yesterday = today.minusDays(1);
		LocalDate from = today.minusDays(LV4_CONSECUTIVE_DAYS + 2L);

		List<Sleep> logs = sleepRepository
				.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId, from);
		Map<LocalDate, Integer> dailyTotals = computeDailyTotals(logs);

		// ---- お知らせ: 今日まだ記録されていない → ここで終了、評価はしない ----
		if (!dailyTotals.containsKey(today)) {
			items.add(buildReminder(
					today,
					"睡眠記録がありません",
					"今日の睡眠データがまだ記録されていません。記録すると、あなたに合ったフィードバックが受け取れます。"));
			return items;
		}

		// ---- お知らせ: 昨日の記録漏れ（今日の評価は継続して行う） ----
		if (!dailyTotals.containsKey(yesterday)) {
			items.add(buildReminder(today,
					"昨日の睡眠記録がありません",
					"昨日分の睡眠データが記録されていません。忘れずに記録しましょう。"));
		}

		// ---- Main evaluation / メイン評価: LV4 > LV3/LV2 > Goal check > LV1 ----
		List<FeedbackItem> mainFeedback = new ArrayList<>();

		checkContinuousShortSleep(dailyTotals, today, mainFeedback); // LV4

		if (mainFeedback.isEmpty()) {
			checkShortAverageSleep(dailyTotals, today, mainFeedback); // LV3 / LV2
		}

		if (mainFeedback.isEmpty()) {
			BigDecimal sleepGoalHours = profile.getSleepGoalHours();
			boolean hasGoal = sleepGoalHours != null && sleepGoalHours.compareTo(BigDecimal.ZERO) > 0;

			if (!hasGoal) {
				mainFeedback.add(new FeedbackItem(
						FeedbackType.SLEEP_NO_GOAL,
						FeedbackLevel.LV0,
						"睡眠の目標が設定されていません",
						"目標を設定すると、あなたに合ったフィードバックが受け取れます。",
						today.atStartOfDay(),
						"target"));
			} else {
				int sleepGoalMinutes = sleepGoalHours
						.multiply(BigDecimal.valueOf(60))
						.intValue();

				checkGoodSleep(dailyTotals, today, sleepGoalMinutes, mainFeedback); // LV1
			}
		}
		items.addAll(mainFeedback);
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
		items.add(new FeedbackItem(FeedbackType.SLEEP_CONTINUOUS_SHORT, FeedbackLevel.LV4, "睡眠不足が続いています",
				LV4_CONSECUTIVE_DAYS + "日連続で6時間未満の睡眠です。十分な休息を取ることをおすすめします。",
				today.atStartOfDay(), "alert-octagon"));
	}

	// ---- Lv3: 直近3日間の平均睡眠時間が5時間未満 ----
	// データが3日分揃っていない場合は、当日単独の即時判定にフォールバックする
	private void checkShortAverageSleep(Map<LocalDate, Integer> dailyTotals, LocalDate today,
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
			checkTodayShortSleep(dailyTotals, today, items);
			return;
		}

		double average = recent.stream().mapToInt(Integer::intValue).average().orElse(0);
		if (average < SHORT_SLEEP_MINUTES_THRESHOLD) {
			items.add(new FeedbackItem(FeedbackType.SLEEP_SHORT, FeedbackLevel.LV3, "睡眠時間が短い日が続いています",
					"直近" + LV3_LOOKBACK_DAYS + "日間の平均睡眠時間が5時間未満です。休息時間を見直しましょう。", today.atStartOfDay(),
					"alert-triangle"));
		}
	}

	// ---- Lv2: 当日の睡眠時間が5時間未満（即時判定・データ不足時のフォールバック） ----
	private void checkTodayShortSleep(Map<LocalDate, Integer> dailyTotals, LocalDate today, List<FeedbackItem> items) {
		Integer minutesToday = dailyTotals.get(today);
		if (minutesToday == null || minutesToday >= SHORT_SLEEP_MINUTES_THRESHOLD) {
			return;
		}
		int hours = minutesToday / 60;
		int mins = minutesToday % 60;
		items.add(new FeedbackItem(FeedbackType.SLEEP_SHORT, FeedbackLevel.LV2, "今日の睡眠時間が短いです",
				"今日の睡眠は" + hours + "時間" + mins + "分でした。十分な休息を心がけましょう。", today.atStartOfDay(), "lightbulb"));
	}

	// ---- LV1: Sleep goal achieved / 睡眠目標達成 ----
	private void checkGoodSleep(
			Map<LocalDate, Integer> dailyTotals,
			LocalDate today,
			int sleepGoalMinutes,
			List<FeedbackItem> items) {
		Integer minutes = dailyTotals.get(today);
		if (minutes == null || minutes < sleepGoalMinutes) {
			return;
		}
		int hours = minutes / 60;
		int mins = minutes % 60;
		items.add(new FeedbackItem(
				FeedbackType.SLEEP_GOOD,
				FeedbackLevel.LV1,
				"睡眠目標達成です",
				"今日の睡眠時間は" + hours + "時間" + mins + "分でした。設定した目標を達成しました！",
				today.atStartOfDay(),
				"check-circle"));
	}

	private FeedbackItem buildReminder(LocalDate today, String title, String message) {
		return new FeedbackItem(FeedbackType.SLEEP_NO_RECORD, FeedbackLevel.LV0, title, message,
				today.atStartOfDay(), "calendar-x");
	}

	private FeedbackItem buildReminder(FeedbackType type, LocalDate today, String title, String message) {
		return new FeedbackItem(type, FeedbackLevel.LV0, title, message, today.atStartOfDay(), "calendar-x");
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