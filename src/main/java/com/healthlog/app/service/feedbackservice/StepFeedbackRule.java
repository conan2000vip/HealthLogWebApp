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

	// 目標達成率
	private static final int ALMOST_MIN_PERCENT = 80;
	private static final int LOW_THRESHOLD_PERCENT = 50;

	// 何日以上記録がない場合に「しばらく記録がありません」を出すか（歩数は1日1回記録が前提のためWeightと同じ3日）
	private static final long NO_RECORD_DAYS_THRESHOLD = 3;

	public List<FeedbackItem> evaluate(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();
		Profile profile = profileRepository.findById(profileId).orElseThrow();

		LocalDate today = LocalDate.now();
		LocalDate yesterday = today.minusDays(1);

		// ---- お知らせ: 一度も記録がない、または長期間記録がない ----
		Optional<Step> lastEverOpt = stepRepository.findTopByProfile_IdOrderByRecordedDateDesc(profileId);
		if (lastEverOpt.isEmpty()) {
			items.add(buildNoRecordReminder(today, "歩数記録がありません", "まだ歩数データが記録されていません。記録を始めてみましょう。"));
			return items;
		}

		long daysSinceLast = ChronoUnit.DAYS.between(lastEverOpt.get().getRecordedDate(), today);
		if (daysSinceLast >= NO_RECORD_DAYS_THRESHOLD) {
			items.add(new FeedbackItem(FeedbackType.STEP_NO_RECORD, FeedbackLevel.LV2, "最近、歩数記録がありません",
					"最後の記録から" + daysSinceLast + "日経っています。今日の歩数を記録してみましょう。",
					lastEverOpt.get().getRecordedDate().atStartOfDay(), "lightbulb"));
			return items;
		}

		List<Step> logs = stepRepository
				.findByProfile_IdAndRecordedDateGreaterThanEqualOrderByRecordedDateDesc(profileId, yesterday);
		Map<LocalDate, Integer> dailyTotals = computeDailyTotals(logs);

		if (!dailyTotals.containsKey(today)) {
			items.add(buildNoRecordReminder(today, "歩数記録がありません", "今日の歩数データがまだ記録されていません。記録すると、あなたに合ったフィードバックが受け取れます。"));
			return items;
		}

		// ---- 今日は記録あり：直前の記録（今日以外）との間隔を見て、空白があれば「再開」を1件だけ表示 ----
		Optional<Step> previousBeforeToday = stepRepository
				.findTopByProfile_IdAndRecordedDateLessThanOrderByRecordedDateDesc(profileId, today);
		if (previousBeforeToday.isPresent()) {
			long totalGap = ChronoUnit.DAYS.between(previousBeforeToday.get().getRecordedDate(), today);
			if (totalGap > 1) {
				long emptyDays = totalGap - 1; // 前回と今日の間に実際に記録が無かった日数
				items.add(new FeedbackItem(FeedbackType.STEP_RESUMED, FeedbackLevel.LV0, "記録を再開しました",
						"前回の記録から" + emptyDays + "日空きましたが、今日また記録できました。この調子で続けましょう。", today.atStartOfDay(),
						"calendar-check"));
			}
		}

		// ---- Main evaluation：目標比などは今日の実データに対してそのまま評価する ----
		List<FeedbackItem> mainFeedback = new ArrayList<>();
		Integer goal = profile.getStepGoal();
		boolean hasGoal = goal != null && goal > 0;

		if (!hasGoal) {
			mainFeedback.add(new FeedbackItem(FeedbackType.STEP_NO_GOAL, FeedbackLevel.LV1, "今日の歩数記録を完了しました",
					"歩数の記録、お疲れさまでした！目標を設定すると、より詳しいフィードバックが受け取れます。", today.atStartOfDay(), "check-circle"));
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

	// 目標の50%未満
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
		items.add(new FeedbackItem(FeedbackType.STEP_LOW, FeedbackLevel.LV3, "歩数が少ないです",
				"現在の歩数は目標の" + rate + "%です。少し体を動かしてみましょう。", today.atStartOfDay(), "alert-triangle"));
	}

	// 目標50%以上100%未満
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
		String title;
		String message;

		if (rate < ALMOST_MIN_PERCENT) {
			// 50～79%
			title = "目標達成に向けて順調です";
			message = "現在 " + rate + "% 達成しています。目標達成に向けて、もう少し歩いてみましょう。";
		} else {
			// 80～99%
			title = "もう少しで目標達成です";
			message = "現在 " + rate + "% 達成しています。あと少しで目標達成です。";
		}
		items.add(new FeedbackItem(FeedbackType.STEP_ALMOST, FeedbackLevel.LV2, title, message, today.atStartOfDay(),
				"lightbulb"));
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
		items.add(new FeedbackItem(FeedbackType.STEP_COMPLETE, FeedbackLevel.LV1, "歩数目標を達成しました",
				"本日の歩数は" + total + "歩です。設定した歩数目標を達成しました！", today.atStartOfDay(), "check-circle"));
	}

	private FeedbackItem buildNoRecordReminder(LocalDate today, String title, String message) {
		return new FeedbackItem(FeedbackType.STEP_NO_RECORD, FeedbackLevel.LV0, title, message, today.atStartOfDay(),
				"calendar-x");
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