package com.healthlog.app.service.feedbackservice;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	/** 目標達成率 */
	private static final int ALMOST_MIN_PERCENT = 80;
	private static final int LOW_THRESHOLD_PERCENT = 50;

	/** 4000ml超過 */
	private static final int MAX_DAILY_AMOUNT = 4000;

	public List<FeedbackItem> evaluate(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();
		Profile profile = profileRepository.findById(profileId).orElseThrow(
				() -> new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND, "プロフィールが見つかりません"));

		LocalDate today = LocalDate.now();
		LocalDate yesterday = today.minusDays(1);

		List<Water> logs = waterRepository
				.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId, yesterday);
		Map<LocalDate, Integer> dailyTotals = computeDailyTotals(logs);

		// ---- お知らせ: 今日まだ記録されていない → ここで終了、評価はしない ----
		if (!dailyTotals.containsKey(today)) {
			items.add(
					buildNoRecordReminder(today, "水分記録がありません", "今日の水分摂取データがまだ記録されていません。記録すると、あなたに合ったフィードバックが受け取れます。"));
			return items;
		}

		// ---- お知らせ: 昨日の記録漏れ（今日の評価は継続して行う） ----
		if (!dailyTotals.containsKey(yesterday)) {
			items.add(buildNoRecordReminder(today, "昨日の水分記録がありません", "昨日分の水分摂取データが記録されていません。忘れずに記録しましょう。"));
		}

		// ---- メイン評価: LV4（目標に依存しない）→ 目標未設定チェック → LV3/LV2/LV1 ----
		List<FeedbackItem> mainFeedback = new ArrayList<>();
		checkTooMuchWater(dailyTotals, today, mainFeedback); // LV4（goal不要）

		Integer goal = profile.getWaterGoalMl();
		boolean hasGoal = Boolean.TRUE.equals(profile.getWaterGoalSet());

		if (mainFeedback.isEmpty()) {
			if (!hasGoal) {
				mainFeedback.add(new FeedbackItem(
						FeedbackType.WATER_NO_GOAL,
						FeedbackLevel.LV0,
						"水分摂取の目標が設定されていません",
						"目標を設定すると、あなたに合ったフィードバックが受け取れます。",
						today.atStartOfDay(),
						"target"));
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

	//1日の摂取量が4000ml超（目標未設定でも判定する）
	private void checkTooMuchWater(Map<LocalDate, Integer> dailyTotals, LocalDate today, List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null || total <= MAX_DAILY_AMOUNT) {
			return;
		}
		items.add(new FeedbackItem(FeedbackType.WATER_EXCESS, FeedbackLevel.LV4, "水分を摂りすぎています", "本日の摂取量は" +
				total + "mlです。必要以上の水分摂取には注意しましょう。", today.atStartOfDay(), "alert-octagon"));
	}

	//目標80%以上達成
	private void checkAlmostGoal(Map<LocalDate, Integer> dailyTotals, LocalDate today, Integer goal,
			List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null) {
			return;
		}
		int rate = (int) Math.round(total * 100.0 / goal);
		if (rate < ALMOST_MIN_PERCENT || rate >= 100) {
			return;
		}
		items.add(new FeedbackItem(FeedbackType.WATER_ALMOST, FeedbackLevel.LV2,
				"もう少しで目標達成です", "現在 " + rate + "% 達成しています。あと少し水分を摂りましょう。", today.atStartOfDay(), "lightbulb"));
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
		items.add(new FeedbackItem(FeedbackType.WATER_COMPLETE, FeedbackLevel.LV1, "目標達成です",
				"本日の水分目標を達成しました。", today.atStartOfDay(), "check-circle"));
	}

	//目標の50%未満
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
		items.add(new FeedbackItem(FeedbackType.WATER_LOW, FeedbackLevel.LV3,
				"水分摂取が不足しています", "現在の摂取量は目標の" + rate + "%です。水分補給を心がけましょう。", today.atStartOfDay(), "alert-triangle"));
	}

	private FeedbackItem buildNoRecordReminder(LocalDate today, String title, String message) {
		return new FeedbackItem(FeedbackType.WATER_NO_RECORD, FeedbackLevel.LV0, title, message,
				today.atStartOfDay(), "calendar-x");
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