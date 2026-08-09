package com.healthlog.app.service.feedbackservice;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.Step;
import com.healthlog.app.repository.ProfileRepository;
import com.healthlog.app.repository.StepRepository;
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;
import com.healthlog.app.service.feedbackservice.model.FeedbackLevel;
import com.healthlog.app.service.feedbackservice.model.FeedbackType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StepFeedbackRule {

	private final StepRepository stepRepository;
	private final ProfileRepository profileRepository;

	//目標達成率
	private static final int ALMOST_MIN_PERCENT = 80;
	private static final int LOW_THRESHOLD_PERCENT = 50;

	public List<FeedbackItem> evaluate(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();
		Profile profile = profileRepository.findById(profileId).orElseThrow();

		LocalDate today = LocalDate.now();
		LocalDate yesterday = today.minusDays(1);

		List<Step> logs = stepRepository
				.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId, yesterday);
		Map<LocalDate, Integer> dailyTotals = computeDailyTotals(logs);

		// ---- お知らせ: 今日まだ記録されていない → ここで終了、評価はしない ----
		if (!dailyTotals.containsKey(today)) {
			items.add(buildNoRecordReminder(today, "歩数記録がありません", "今日の歩数データがまだ記録されていません。記録すると、あなたに合ったフィードバックが受け取れます。"));
			return items;
		}

		// ---- お知らせ: 昨日の記録漏れ（今日の評価は継続して行う） ----
		if (!dailyTotals.containsKey(yesterday)) {
			items.add(buildNoRecordReminder(today, "昨日の歩数記録がありません", "昨日分の歩数データが記録されていません。忘れずに記録しましょう。"));
		}

		// ---- Main evaluation / メイン評価: Goal check → LV3/LV2/LV1 ----
		List<FeedbackItem> mainFeedback = new ArrayList<>();
		Integer goal = profile.getStepGoal();
		boolean hasGoal = goal != null && goal > 0;

		if (!hasGoal) {
			mainFeedback.add(new FeedbackItem(
					FeedbackType.STEP_NO_GOAL,
					FeedbackLevel.LV0,
					"歩数の目標が設定されていません",
					"目標を設定すると、あなたに合ったフィードバックが受け取れます。",
					today.atStartOfDay(),
					"target"));
		} else {
			checkLowSteps(dailyTotals, today, goal, mainFeedback); // LV3
			if (mainFeedback.isEmpty()) {
				checkAlmostGoal(dailyTotals, today, goal, mainFeedback); // LV2
			}
			if (mainFeedback.isEmpty()) {
				checkComplete(dailyTotals, today, goal, mainFeedback); // LV1
			}
		}
		items.addAll(mainFeedback);
		return items;
	}

	//目標の50%未満
	private void checkLowSteps(Map<LocalDate, Integer> dailyTotals, LocalDate today, Integer goal,
			List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null) {
			return;
		}
		int rate = (int) Math.round(total * 100.0 / goal);
		if (rate >= LOW_THRESHOLD_PERCENT) {
			return;
		}
		items.add(new FeedbackItem(FeedbackType.STEP_LOW, FeedbackLevel.LV3,
				"歩数が少ないです", "現在の歩数は目標の" + rate + "%です。少し体を動かしてみましょう。", today.atStartOfDay(), "alert-triangle"));
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
		items.add(new FeedbackItem(FeedbackType.STEP_ALMOST, FeedbackLevel.LV2,
				"もう少しで目標達成です", "現在 " + rate + "% 達成しています。あと少し歩いてみましょう。", today.atStartOfDay(), "lightbulb"));
	}

	private void checkComplete(Map<LocalDate, Integer> dailyTotals, LocalDate today, Integer goal,
			List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null) {
			return;
		}
		int rate = (int) Math.round(total * 100.0 / goal);
		if (rate < 100) {
			return;
		}
		items.add(new FeedbackItem(FeedbackType.STEP_COMPLETE, FeedbackLevel.LV1, "目標達成です",
				"本日の歩数目標を達成しました。", today.atStartOfDay(), "check-circle"));
	}

	private FeedbackItem buildNoRecordReminder(LocalDate today, String title, String message) {
		return new FeedbackItem(FeedbackType.STEP_NO_RECORD, FeedbackLevel.LV0, title, message,
				today.atStartOfDay(), "calendar-x");
	}

	// 日ごとの合計歩数
	private Map<LocalDate, Integer> computeDailyTotals(List<Step> logs) {
		Map<LocalDate, Integer> map = new HashMap<>();
		for (Step s : logs) {
			int steps = s.getSteps() == null ? 0 : s.getSteps();
			map.merge(s.getRecordedDate(), steps, Integer::sum);
		}
		return map;
	}
}