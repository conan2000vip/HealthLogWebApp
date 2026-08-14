package com.healthlog.app.service.feedbackservice;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.Water;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.repository.ProfileRepository;
import com.healthlog.app.repository.WaterRepository;
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;
import com.healthlog.app.service.feedbackservice.model.FeedbackLevel;
import com.healthlog.app.service.feedbackservice.model.FeedbackType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WaterFeedbackRule {

	private final WaterRepository waterRepository;
	private final ProfileRepository profileRepository;

	private static final int ALMOST_MIN_PERCENT = 80;
	private static final int LOW_THRESHOLD_PERCENT = 50;

	private static final int MAX_DAILY_AMOUNT = 4000;
	private static final long NO_RECORD_DAYS_THRESHOLD = 2;

	public List<FeedbackItem> evaluate(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();
		Profile profile = profileRepository.findById(profileId).orElseThrow(
				() -> new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND, "プロフィールが見つかりません"));

		LocalDate today = LocalDate.now();
		LocalDate yesterday = today.minusDays(1);

		// ---- お知らせ: 一度も記録がない、またはしばらく記録がない ----
		Optional<Water> lastEverOpt = waterRepository.findTopByProfile_IdOrderByRecordedDateDesc(profileId);
		if (lastEverOpt.isEmpty()) {
			items.add(buildNoRecordReminder(today, "水分記録がありません", "まだ水分データが記録されていません。記録を始めてみましょう。"));
			return items;
		}
		long daysSinceLast = ChronoUnit.DAYS.between(lastEverOpt.get().getRecordedDate(), today);
		if (daysSinceLast >= NO_RECORD_DAYS_THRESHOLD) {
			items.add(new FeedbackItem(FeedbackType.WATER_NO_RECORD, FeedbackLevel.LV2, "最近、水分記録がありません",
					"最後の記録から" + daysSinceLast + "日経っています。今日の水分摂取を記録してみましょう。",
					lastEverOpt.get().getRecordedDate().atStartOfDay(), "lightbulb"));
			return items;
		}

		List<Water> logs = waterRepository
				.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId, yesterday);
		Map<LocalDate, Integer> dailyTotals = computeDailyTotals(logs);

		// ---- お知らせ: 今日まだ記録されていない → ここで終了、評価はしない ----
		if (!dailyTotals.containsKey(today)) {
			items.add(
					buildNoRecordReminder(today, "水分記録がありません", "今日の水分摂取データがまだ記録されていません。記録すると、あなたに合ったフィードバックが受け取れます。"));
			return items;
		}

		// ---- 今日は記録あり：直前の記録日との間隔を確認 ----
		Optional<Water> previousBeforeToday = waterRepository
				.findTopByProfile_IdAndRecordedDateLessThanOrderByRecordedDateDesc(profileId, today);
		if (previousBeforeToday.isPresent()) {
			long totalGap = ChronoUnit.DAYS.between(previousBeforeToday.get().getRecordedDate(), today);
			if (totalGap > 1) {
				long emptyDays = totalGap - 1; // 前回と今日の間に実際に記録が無かった日数
				items.add(new FeedbackItem(FeedbackType.WATER_RESUMED, FeedbackLevel.LV0, "記録を再開しました",
						"前回の記録から" + emptyDays + "日空きましたが、今日また記録できました。この調子で続けましょう。", today.atStartOfDay(),
						"calendar-check"));
			}
		}

		// ---- Main evaluation: LV4 → Goal check → LV3/LV2/LV1 ----
		List<FeedbackItem> mainFeedback = new ArrayList<>();
		Integer goal = profile.getWaterGoalMl();
		boolean hasGoal = goal != null && goal > 0;

		if (mainFeedback.isEmpty()) {
			// LV4: 4000ml超は目標設定の有無に関係なく判定
			checkTooMuchWater(dailyTotals, today, mainFeedback);
			if (mainFeedback.isEmpty()) {
				if (!hasGoal) {
					mainFeedback.add(new FeedbackItem(FeedbackType.WATER_NO_GOAL, FeedbackLevel.LV0,
							"水分摂取の目標が設定されていません", "目標を設定すると、あなたに合ったフィードバックが受け取れます。", today.atStartOfDay(), "target"));
				} else {
					checkLowWater(dailyTotals, today, goal, mainFeedback);
					if (mainFeedback.isEmpty()) {
						checkAlmostGoal(dailyTotals, today, goal, mainFeedback);
					}
					if (mainFeedback.isEmpty()) {
						checkComplete(dailyTotals, today, goal, mainFeedback);
					}
				}
			}
		}
		items.addAll(mainFeedback);
		return items;
	}

	// 1日の摂取量が4000ml超（目標未設定でも判定する）
	private void checkTooMuchWater(Map<LocalDate, Integer> dailyTotals, LocalDate today, List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null || total <= MAX_DAILY_AMOUNT) {
			return;
		}
		items.add(new FeedbackItem(FeedbackType.WATER_EXCESS, FeedbackLevel.LV4, "水分を摂りすぎています",
				"本日の摂取量は" + total + "mlです。必要以上の水分摂取には注意しましょう。", today.atStartOfDay(), "alert-octagon"));
	}

	// 目標の50～99% → LV2
	private void checkAlmostGoal(Map<LocalDate, Integer> dailyTotals, LocalDate today, Integer goal,
			List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null) {
			return;
		}
		int rate = (int) Math.round(total * 100.0 / goal);
		if (rate < LOW_THRESHOLD_PERCENT || rate >= 100) {
			return;
		}
		int remaining = goal - total;
		if (rate >= ALMOST_MIN_PERCENT) {
			items.add(new FeedbackItem(FeedbackType.WATER_ALMOST, FeedbackLevel.LV2, "もう少しで目標達成です",
					"現在 " + rate + "% 達成しています。目標まであと" + remaining + "mlです。", today.atStartOfDay(), "lightbulb"));
		} else {
			items.add(new FeedbackItem(FeedbackType.WATER_ALMOST, FeedbackLevel.LV2, "目標に向けて順調です",
					"現在 " + rate + "% 達成しています。目標まであと" + remaining + "mlです。", today.atStartOfDay(), "lightbulb"));
		}
	}

	private void checkComplete(Map<LocalDate, Integer> dailyTotals, LocalDate today, Integer goal,
			List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null || total > MAX_DAILY_AMOUNT) {
			return;
		}
		int rate = (int) Math.round(total * 100.0 / goal);
		if (rate < 100) {
			return;
		}
		items.add(new FeedbackItem(FeedbackType.WATER_COMPLETE, FeedbackLevel.LV1, "水分目標を達成しました",
				"本日の水分摂取量は" + total + "mlです。設定した水分目標を達成しました！", today.atStartOfDay(), "check-circle"));
	}

	// 目標の50%未満
	private void checkLowWater(Map<LocalDate, Integer> dailyTotals, LocalDate today, Integer goal,
			List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null) {
			return;
		}
		int rate = (int) Math.round(total * 100.0 / goal);
		if (rate >= LOW_THRESHOLD_PERCENT) {
			return;
		}
		int remaining = goal - total;
		items.add(new FeedbackItem(FeedbackType.WATER_LOW, FeedbackLevel.LV3, "水分摂取が不足しています",
				"現在の摂取量は目標の" + rate + "%です。目標まであと" + remaining + "mlです。", today.atStartOfDay(), "alert-triangle"));
	}

	private FeedbackItem buildNoRecordReminder(LocalDate today, String title, String message) {
		return new FeedbackItem(FeedbackType.WATER_NO_RECORD, FeedbackLevel.LV0, title, message, today.atStartOfDay(),
				"calendar-x");
	}

	// 日ごとの合計摂取量
	private Map<LocalDate, Integer> computeDailyTotals(List<Water> logs) {
		Map<LocalDate, Integer> map = new HashMap<>();
		for (Water w : logs) {
			int amount = w.getAmountMl() == null ? 0 : w.getAmountMl();
			map.merge(w.getRecordedDate(), amount, Integer::sum);
		}
		return map;
	}
}