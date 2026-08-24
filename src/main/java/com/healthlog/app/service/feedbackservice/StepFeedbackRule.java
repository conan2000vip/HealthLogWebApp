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

	private static final int ALMOST_MIN_PERCENT = 80;
	private static final int LOW_THRESHOLD_PERCENT = 50;
	private static final long NO_RECORD_DAYS_THRESHOLD = 3;

	// ★ THÊM MỚI: Các Hằng số cho Ngưỡng LV4 (Số bước bất thường)
	private static final int LV4_SUDDEN_HIGH_STEPS = 60000; // Số bước cực lớn trong 1 ngày
	private static final int LV4_SUDDEN_STEP_DIFF = 40000; // Chênh lệch cực lớn so với ngày hôm trước

	public List<FeedbackItem> evaluate(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();
		Profile profile = profileRepository.findById(profileId).orElseThrow();

		LocalDate today = LocalDate.now();
		LocalDate yesterday = today.minusDays(1);

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

		Optional<Step> previousBeforeToday = stepRepository
				.findTopByProfile_IdAndRecordedDateLessThanOrderByRecordedDateDesc(profileId, today);
		if (previousBeforeToday.isPresent()) {
			long totalGap = ChronoUnit.DAYS.between(previousBeforeToday.get().getRecordedDate(), today);
			if (totalGap > 1) {
				long emptyDays = totalGap - 1;
				items.add(new FeedbackItem(FeedbackType.STEP_RESUMED, FeedbackLevel.LV0, "記録を再開しました",
						"前回の記録から" + emptyDays + "日空きましたが、今日また記録できました。この調子で続けましょう。", today.atStartOfDay(),
						"calendar-check"));
			}
		}

		List<FeedbackItem> mainFeedback = new ArrayList<>();

		// ★ THÊM MỚI: Ưu tiên kiểm tra Bất thường LV4 ĐẦU TIÊN (Cho cả trường hợp có
		// hoặc không có mục tiêu)
		checkSuddenStepChange(dailyTotals, today, yesterday, mainFeedback);

		// Nếu không phát hiện bất thường LV4 thì mới kiểm tra tiến độ Mục tiêu (LV3 ->
		// LV2 -> LV1 -> LV0)
		if (mainFeedback.isEmpty()) {
			Integer goal = profile.getStepGoal();
			boolean hasGoal = goal != null && goal > 0;

			if (!hasGoal) {
				mainFeedback.add(new FeedbackItem(FeedbackType.STEP_NO_GOAL, FeedbackLevel.LV0, "歩数の目標が設定されていません",
						"目標を設定すると、より詳しいフィードバックが受け取れます。", today.atStartOfDay(), "target"));
			} else {
				checkLowSteps(dailyTotals, today, goal, mainFeedback); // LV3
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

	// ★ THÊM MỚI: Hàm kiểm tra số bước bất thường (LV4)
	private void checkSuddenStepChange(Map<LocalDate, Integer> dailyTotals, LocalDate today, LocalDate yesterday,
			List<FeedbackItem> items) {
		Integer todaySteps = dailyTotals.get(today);
		if (todaySteps == null)
			return;

		// Trường hợp 1: Nhập số bước cực lớn (>= 60,000 bước)
		if (todaySteps >= LV4_SUDDEN_HIGH_STEPS) {
			items.add(new FeedbackItem(FeedbackType.STEP_SUDDEN_CHANGE, FeedbackLevel.LV4, "急激な歩数変化があります",
					"本日の歩数（" + todaySteps + "歩）が非常に大きいです。入力内容を確認してください。", today.atStartOfDay(), "alert-octagon"));
			return;
		}

		// Trường hợp 2: Chênh lệch quá lớn so với ngày hôm trước (>= 40,000 bước)
		Integer yesterdaySteps = dailyTotals.get(yesterday);
		if (yesterdaySteps != null) {
			int diff = Math.abs(todaySteps - yesterdaySteps);
			if (diff >= LV4_SUDDEN_STEP_DIFF) {
				items.add(new FeedbackItem(FeedbackType.STEP_SUDDEN_CHANGE, FeedbackLevel.LV4, "急激な歩数変化があります",
						"前日比で " + diff + "歩の大きな変化がありました。入力内容を確認してください。", today.atStartOfDay(), "alert-octagon"));
			}
		}
	}

	private void checkLowSteps(Map<LocalDate, Integer> dailyTotals, LocalDate today, Integer goal,
			List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null)
			return;
		int rate = (int) Math.round(total * 100.0 / goal);
		if (rate >= LOW_THRESHOLD_PERCENT)
			return;
		int remaining = goal - total;
		items.add(new FeedbackItem(FeedbackType.STEP_LOW, FeedbackLevel.LV3, "歩数が少ないです",
				"現在の歩数は目標の" + rate + "%です。目標まであと" + remaining + "歩です。", today.atStartOfDay(), "alert-triangle"));
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
		String title = rate < ALMOST_MIN_PERCENT ? "目標達成に向けて順調です" : "もう少しで目標達成です";
		String message = "現在 " + rate + "% 達成しています。目標まであと" + remaining + "歩です。";
		items.add(new FeedbackItem(FeedbackType.STEP_ALMOST, FeedbackLevel.LV2, title, message, today.atStartOfDay(),
				"lightbulb"));
	}

	private void checkComplete(Map<LocalDate, Integer> dailyTotals, LocalDate today, Integer goal,
			List<FeedbackItem> items) {
		Integer total = dailyTotals.get(today);
		if (total == null)
			return;
		int rate = (int) Math.round(total * 100.0 / goal);
		if (rate < 100)
			return;
		items.add(new FeedbackItem(FeedbackType.STEP_COMPLETE, FeedbackLevel.LV1, "歩数目標を達成しました",
				"本日の歩数は" + total + "歩です。設定した歩数目標を達成しました！", today.atStartOfDay(), "check-circle"));
	}

	private FeedbackItem buildNoRecordReminder(LocalDate today, String title, String message) {
		return new FeedbackItem(FeedbackType.STEP_NO_RECORD, FeedbackLevel.LV0, title, message, today.atStartOfDay(),
				"calendar-x");
	}

	private Map<LocalDate, Integer> computeDailyTotals(List<Step> logs) {
		Map<LocalDate, Integer> map = new HashMap<>();
		for (Step s : logs) {
			int steps = s.getSteps() == null ? 0 : s.getSteps();
			map.merge(s.getRecordedDate(), steps, Integer::sum);
		}
		return map;
	}
}