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
	// SỬA: Chuẩn hóa về 3 ngày giống Weight, Sleep, Step
	private static final long NO_RECORD_DAYS_THRESHOLD = 3;

	public List<FeedbackItem> evaluate(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();
		Profile profile = profileRepository.findById(profileId).orElseThrow(
				() -> new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND, "プロフィールが見つかりません"));

		LocalDate today = LocalDate.now();
		LocalDate yesterday = today.minusDays(1);

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

		if (!dailyTotals.containsKey(today)) {
			items.add(
					buildNoRecordReminder(today, "水分記録がありません", "今日の水分摂取データがまだ記録されていません。記録すると、あなたに合ったフィードバックが受け取れます。"));
			return items;
		}

		Optional<Water> previousBeforeToday = waterRepository
				.findTopByProfile_IdAndRecordedDateLessThanOrderByRecordedDateDesc(profileId, today);
		if (previousBeforeToday.isPresent()) {
			long totalGap = ChronoUnit.DAYS.between(previousBeforeToday.get().getRecordedDate(), today);
			if (totalGap > 1) {
				long emptyDays = totalGap - 1;
				items.add(new FeedbackItem(FeedbackType.WATER_RESUMED, FeedbackLevel.LV0, "記録を再開しました",
						"前回の記録から" + emptyDays + "日空きましたが、今日また記録できました。この調子で続けましょう。", today.atStartOfDay(),
						"calendar-check"));
			}
		}

		// Main evaluation: LV4 → Goal check → LV3/LV2/LV1
		List<FeedbackItem> mainFeedback = new ArrayList<>();
		Integer goal = profile.getWaterGoalMl();
		boolean hasGoal = goal != null && goal > 0;

		// 1. Kiểm tra LV4 (Uống quá 4000ml)
		checkTooMuchWater(dailyTotals, today, mainFeedback);

		// 2. Nếu không dính LV4 mới kiểm tra Tiến độ mục tiêu
		if (mainFeedback.isEmpty()) {
			if (!hasGoal) {
				mainFeedback.add(new FeedbackItem(FeedbackType.WATER_NO_GOAL, FeedbackLevel.LV0, "水分摂取の目標が設定されていません",
						"目標を設定すると、あなたに合ったフィードバックが受け取れます。", today.atStartOfDay(), "target"));
			} else {
				checkLowWater(dailyTotals, today, goal, mainFeedback); // LV3
				if (mainFeedback.isEmpty()) {
					checkAlmostGoal(dailyTotals, today, goal, mainFeedback); // LV2
				}
				if (mainFeedback.isEmpty()) {
					checkComplete(dailyTotals, today, goal, mainFeedback); // LV1
				}
			}
		}
		items.addAll(mainFeedback);
		return items;
	}

	// SỬA: Đổi > thành >= MAX_DAILY_AMOUNT để bắt đúng mốc 4000ml
	private void checkTooMuchWater(Map<LocalDate, Integer> dailyTotals, LocalDate today, List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null || total < MAX_DAILY_AMOUNT) {
			return;
		}
		items.add(new FeedbackItem(FeedbackType.WATER_EXCESS, FeedbackLevel.LV4, "水分を摂りすぎています",
				"本日の摂取量は" + total + "mlです。必要以上の水分摂取には注意しましょう。", today.atStartOfDay(), "alert-octagon"));
	}

	private void checkAlmostGoal(Map<LocalDate, Integer> dailyTotals, LocalDate today, Integer goal,
			List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null)
			return;
		int rate = (int) Math.round(total * 100.0 / goal);
		if (rate < LOW_THRESHOLD_PERCENT || rate >= 100)
			return;
		int remaining = goal - total;
		String title = rate >= ALMOST_MIN_PERCENT ? "もう少しで目標達成です" : "目標に向けて順調です";
		items.add(new FeedbackItem(FeedbackType.WATER_ALMOST, FeedbackLevel.LV2, title,
				"現在 " + rate + "% 達成しています。目標まであと" + remaining + "mlです。", today.atStartOfDay(), "lightbulb"));
	}

	private void checkComplete(Map<LocalDate, Integer> dailyTotals, LocalDate today, Integer goal,
			List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		// SỬA: Loại bỏ vế total > MAX_DAILY_AMOUNT vì đã được checkTooMuchWater xử lý
		if (total == null)
			return;
		int rate = (int) Math.round(total * 100.0 / goal);
		if (rate < 100)
			return;
		items.add(new FeedbackItem(FeedbackType.WATER_COMPLETE, FeedbackLevel.LV1, "水分目標を達成しました",
				"本日の水分摂取量は" + total + "mlです。設定した水分目標を達成しました！", today.atStartOfDay(), "check-circle"));
	}

	private void checkLowWater(Map<LocalDate, Integer> dailyTotals, LocalDate today, Integer goal,
			List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null)
			return;
		int rate = (int) Math.round(total * 100.0 / goal);
		if (rate >= LOW_THRESHOLD_PERCENT)
			return;
		int remaining = goal - total;
		items.add(new FeedbackItem(FeedbackType.WATER_LOW, FeedbackLevel.LV3, "水分摂取が不足しています",
				"現在の摂取量は目標の" + rate + "%です。目標まであと" + remaining + "mlです。", today.atStartOfDay(), "alert-triangle"));
	}

	private FeedbackItem buildNoRecordReminder(LocalDate today, String title, String message) {
		return new FeedbackItem(FeedbackType.WATER_NO_RECORD, FeedbackLevel.LV0, title, message, today.atStartOfDay(),
				"calendar-x");
	}

	private Map<LocalDate, Integer> computeDailyTotals(List<Water> logs) {
		Map<LocalDate, Integer> map = new HashMap<>();
		for (Water w : logs) {
			int amount = w.getAmountMl() == null ? 0 : w.getAmountMl();
			map.merge(w.getRecordedDate(), amount, Integer::sum);
		}
		return map;
	}
}