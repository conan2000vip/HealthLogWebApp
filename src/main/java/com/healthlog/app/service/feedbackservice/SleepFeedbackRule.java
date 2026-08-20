package com.healthlog.app.service.feedbackservice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
	private static final int SHORT_SLEEP_MINUTES_THRESHOLD = 5 * 60; // 5時間

	private static final int LV4_CONSECUTIVE_DAYS = 5;
	private static final int LV4_DAILY_MINUTES_THRESHOLD = 6 * 60; // 6時間

	private static final long NO_RECORD_DAYS_THRESHOLD = 3;

	public List<FeedbackItem> evaluate(Long profileId) {
		Profile profile = profileRepository.findById(profileId).orElseThrow();
		List<FeedbackItem> items = new ArrayList<>();
		LocalDate today = LocalDate.now();

		// ---- お知らせ: 一度も記録がない ----
		Optional<Sleep> lastEverOpt = sleepRepository.findTopByProfile_IdOrderByRecordedDateDesc(profileId);
		if (lastEverOpt.isEmpty()) {
			items.add(buildReminder(today, "睡眠記録がありません", "まだ睡眠データが記録されていません。記録を始めてみましょう。"));
			return items;
		}

		LocalDate lastRecordedDate = lastEverOpt.get().getRecordedDate();
		long daysSinceLast = ChronoUnit.DAYS.between(lastRecordedDate, today);

		LocalDate from = lastRecordedDate.minusDays(LV4_CONSECUTIVE_DAYS - 1L);
		List<Sleep> logs = sleepRepository
				.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId, from);
		Map<LocalDate, Integer> dailyTotals = computeDailyTotals(logs);

		// ---- Lv4: 直近5日連続で睡眠時間が6時間未満 ----
		List<FeedbackItem> mainFeedback = new ArrayList<>();
		checkContinuousShortSleep(dailyTotals, lastRecordedDate, mainFeedback);

		// ---- お知らせ: しばらく記録がない（3日以上） ----
		if (daysSinceLast >= NO_RECORD_DAYS_THRESHOLD) {
			items.add(new FeedbackItem(FeedbackType.SLEEP_NO_RECORD, FeedbackLevel.LV2, "最近、睡眠記録がありません",
					"最後の記録から" + daysSinceLast + "日経っています。今日の睡眠を記録してみましょう。", lastRecordedDate.atStartOfDay(),
					"lightbulb"));
			items.addAll(mainFeedback);
			return items;
		}

		// ---- お知らせ: 今日まだ記録されていない ----
		if (!dailyTotals.containsKey(today)) {
			items.add(buildReminder(today, "睡眠記録がありません", "今日の睡眠データがまだ記録されていません。記録すると、あなたに合ったフィードバックが受け取れます。"));
			items.addAll(mainFeedback);
			return items;
		}

		// ---- 今日は記録あり：直前の記録日との間隔を確認 ----
		Optional<Sleep> previousBeforeToday = sleepRepository
				.findTopByProfile_IdAndRecordedDateLessThanOrderByRecordedDateDesc(profileId, today);
		if (previousBeforeToday.isPresent()) {
			long totalGap = ChronoUnit.DAYS.between(previousBeforeToday.get().getRecordedDate(), today);
			if (totalGap > 1) {
				long emptyDays = totalGap - 1;
				items.add(new FeedbackItem(FeedbackType.SLEEP_RESUMED, FeedbackLevel.LV0, "記録を再開しました",
						"前回の記録から" + emptyDays + "日空きましたが、今日また記録できました。この調子で続けましょう。", today.atStartOfDay(),
						"calendar-check"));
			}
		}

		// ---- Main evaluation: LV4 > LV3 > Goal check (LV2/LV1) ----
		if (mainFeedback.isEmpty()) {
			checkShortAverageSleep(dailyTotals, today, mainFeedback); // LV3
		}

		if (mainFeedback.isEmpty()) {
			BigDecimal sleepGoalHours = profile.getSleepGoalHours();
			boolean hasGoal = sleepGoalHours != null && sleepGoalHours.compareTo(BigDecimal.ZERO) > 0;

			if (!hasGoal) {
				mainFeedback.add(new FeedbackItem(FeedbackType.SLEEP_NO_GOAL, FeedbackLevel.LV0, "睡眠の目標が設定されていません",
						"目標を設定すると、あなたに合ったフィードバックが受け取れます。", today.atStartOfDay(), "target"));
			} else {
				int sleepGoalMinutes = sleepGoalHours.multiply(BigDecimal.valueOf(60)).intValue();
				checkSleepGoalRate(dailyTotals, today, sleepGoalMinutes, mainFeedback);
			}
		}
		items.addAll(mainFeedback);
		return items;
	}

	private void checkContinuousShortSleep(Map<LocalDate, Integer> dailyTotals, LocalDate referenceDate,
			List<FeedbackItem> items) {
		for (int i = 0; i < LV4_CONSECUTIVE_DAYS; i++) {
			LocalDate date = referenceDate.minusDays(i);
			Integer minutes = dailyTotals.get(date);
			if (minutes == null || minutes >= LV4_DAILY_MINUTES_THRESHOLD) {
				return;
			}
		}
		items.add(new FeedbackItem(FeedbackType.SLEEP_CONTINUOUS_SHORT, FeedbackLevel.LV4, "睡眠不足が続いています",
				LV4_CONSECUTIVE_DAYS + "日連続で6時間未満の睡眠です。十分な休息を取ることをおすすめします。", referenceDate.atStartOfDay(),
				"alert-octagon"));
	}

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
		// Đủ 3 ngày và trung bình < 5h
		if (recent.size() == LV3_LOOKBACK_DAYS) {
			double average = recent.stream().mapToInt(Integer::intValue).average().orElse(0);
			if (average < SHORT_SLEEP_MINUTES_THRESHOLD) {
				items.add(new FeedbackItem(FeedbackType.SLEEP_SHORT, FeedbackLevel.LV3, "睡眠不足が継続しています",
						"直近" + LV3_LOOKBACK_DAYS + "日間の平均睡眠時間が5時間未満です。休息時間を見直しましょう。", today.atStartOfDay(),
						"alert-triangle"));
				return;
			}
		}
		// Nếu không đủ 3 ngày liên tiếp hoặc trung bình >= 5h thì check riêng hôm nay
		checkTodayShortSleep(dailyTotals, today, items);
	}

	private void checkTodayShortSleep(Map<LocalDate, Integer> dailyTotals, LocalDate today, List<FeedbackItem> items) {
		Integer minutesToday = dailyTotals.get(today);
		if (minutesToday == null || minutesToday >= SHORT_SLEEP_MINUTES_THRESHOLD) {
			return;
		}
		int hours = minutesToday / 60;
		int mins = minutesToday % 60;

		items.add(new FeedbackItem(FeedbackType.SLEEP_SHORT, FeedbackLevel.LV3, "今日の睡眠時間が短いです",
				"今日の睡眠は" + hours + "時間" + mins + "分でした。十分な休息を心がけましょう。", today.atStartOfDay(), "alert-triangle"));
	}

	private void checkSleepGoalRate(Map<LocalDate, Integer> dailyTotals, LocalDate today, int sleepGoalMinutes,
			List<FeedbackItem> items) {
		Integer minutes = dailyTotals.get(today);
		if (minutes == null || sleepGoalMinutes <= 0) {
			return;
		}

		double rate = minutes * 100.0 / sleepGoalMinutes;
		int displayRate = (int) Math.round(rate);

		if (rate < 50) {
			int remaining = sleepGoalMinutes - minutes;
			items.add(new FeedbackItem(FeedbackType.SLEEP_SHORT, FeedbackLevel.LV2, "睡眠時間が目標よりかなり短いです",
					"現在の睡眠時間は目標の" + displayRate + "%です。目標まであと" + formatMinutes(remaining) + "です。", today.atStartOfDay(),
					"lightbulb"));
		} else if (rate < 100) {
			int remaining = sleepGoalMinutes - minutes;
			String title = rate >= 80 ? "もう少しで睡眠目標達成です" : "睡眠目標に向けて順調です";
			String message = "現在 " + displayRate + "% 達成しています。目標まであと" + formatMinutes(remaining) + "です。";
			items.add(new FeedbackItem(FeedbackType.SLEEP_SHORT, FeedbackLevel.LV2, title, message,
					today.atStartOfDay(), "lightbulb"));
		} else {
			int hours = minutes / 60;
			int mins = minutes % 60;
			items.add(new FeedbackItem(FeedbackType.SLEEP_GOOD, FeedbackLevel.LV1, "睡眠目標を達成しました",
					"今日の睡眠時間は" + hours + "時間" + mins + "分です。設定した睡眠目標を達成しました！", today.atStartOfDay(), "check-circle"));
		}
	}

	private String formatMinutes(int totalMinutes) {
		int hours = totalMinutes / 60;
		int mins = totalMinutes % 60;
		if (hours > 0) {
			return hours + "時間" + mins + "分";
		}
		return mins + "分";
	}

	private FeedbackItem buildReminder(LocalDate today, String title, String message) {
		return new FeedbackItem(FeedbackType.SLEEP_NO_RECORD, FeedbackLevel.LV0, title, message, today.atStartOfDay(),
				"calendar-x");
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