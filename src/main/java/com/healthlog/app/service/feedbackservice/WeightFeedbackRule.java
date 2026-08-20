package com.healthlog.app.service.feedbackservice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.Weight;
import com.healthlog.app.repository.ProfileRepository;
import com.healthlog.app.repository.WeightRepository;
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;
import com.healthlog.app.service.feedbackservice.model.FeedbackLevel;
import com.healthlog.app.service.feedbackservice.model.FeedbackType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WeightFeedbackRule {

	private final WeightRepository weightRepository;
	private final ProfileRepository profileRepository;

	private static final BigDecimal LV3_CHANGE_PERCENT = BigDecimal.valueOf(3);
	private static final BigDecimal LV4_CHANGE_PERCENT = BigDecimal.valueOf(5);
	private static final BigDecimal LV4_DAILY_CHANGE_KG = BigDecimal.valueOf(2);
	private static final long NO_RECORD_DAYS_THRESHOLD = 3;
	private static final BigDecimal TARGET_ACHIEVED_TOLERANCE_KG = BigDecimal.valueOf(0.5);

	public List<FeedbackItem> evaluate(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();
		Profile profile = profileRepository.findById(profileId).orElseThrow();
		LocalDate today = LocalDate.now();

		List<Weight> logs = weightRepository.findByProfile_IdOrderByRecordedDateDesc(profileId);
		if (logs.isEmpty()) {
			items.add(buildNoRecordReminder(today, "体重記録がありません", "まだ体重データが記録されていません。記録を始めてみましょう。"));
			return items;
		}
		List<Weight> latestLogs = latestPerDateDesc(logs);
		Weight latest = latestLogs.get(0);
		long daysSinceLast = ChronoUnit.DAYS.between(latest.getRecordedDate(), today);

		if (daysSinceLast >= NO_RECORD_DAYS_THRESHOLD) {
			items.add(new FeedbackItem(FeedbackType.WEIGHT_NO_RECORD, FeedbackLevel.LV2, "最近、体重記録がありません",
					"最後の記録から" + daysSinceLast + "日経っています。今日の状態を記録してみましょう。", latest.getRecordedDate().atStartOfDay(),
					"lightbulb"));
			return items;
		}

		boolean hasToday = latestLogs.stream().anyMatch(w -> w.getRecordedDate().isEqual(today));

		if (!hasToday) {
			items.add(buildNoRecordReminder(today, "体重記録がありません", "今日の体重データがまだ記録されていません。記録すると、あなたに合ったフィードバックが受け取れます。"));
		} else if (latestLogs.size() >= 2) {
			LocalDate previousDate = latestLogs.get(1).getRecordedDate();
			long totalGap = ChronoUnit.DAYS.between(previousDate, today);
			if (totalGap > 1) {
				long emptyDays = totalGap - 1;
				items.add(new FeedbackItem(FeedbackType.WEIGHT_RESUMED, FeedbackLevel.LV0, "記録を再開しました",
						"前回の記録から" + emptyDays + "日空きましたが、今日また記録できました。この調子で続けましょう。", today.atStartOfDay(),
						"calendar-check"));
			}
		}

		// Main evaluation: LV4/LV3 (Thay đổi bất thường) -> LV1/LV2 (Tiến độ mục tiêu)
		List<FeedbackItem> mainFeedback = new ArrayList<>();
		checkWeightChange(latestLogs, mainFeedback);
		if (mainFeedback.isEmpty()) {
			checkAchievement(latestLogs, profile, today, mainFeedback);
		}
		items.addAll(mainFeedback);
		return items;
	}

	private void checkAchievement(List<Weight> logs, Profile profile, LocalDate today, List<FeedbackItem> items) {
		Weight latest = logs.get(0);
		BigDecimal current = latest.getWeight();
		BigDecimal target = profile.getTargetWeight();

		if (current == null)
			return;

		boolean hasGoal = target != null && target.compareTo(BigDecimal.ZERO) > 0;

		if (!hasGoal) {
			if (latest.getRecordedDate().isEqual(today)) {
				items.add(new FeedbackItem(FeedbackType.DAILY_COMPLETE, FeedbackLevel.LV0, "今日の健康記録を完了しました",
						"体重の記録、お疲れさまでした！目標体重を設定すると、より詳しいフィードバックが受け取れます。", latest.getMeasuredAt(), "check-circle"));
			}
			return;
		}

		BigDecimal diff = current.subtract(target);
		BigDecimal absDiff = diff.abs();

		if (absDiff.compareTo(TARGET_ACHIEVED_TOLERANCE_KG) <= 0) {
			items.add(new FeedbackItem(FeedbackType.WEIGHT_GOAL_ACHIEVED, FeedbackLevel.LV1, "目標体重を達成しました",
					"現在の体重は" + current + "kgです。設定した目標体重に達しています！", latest.getMeasuredAt(), "check-circle"));
			return;
		}

		if (!latest.getRecordedDate().isEqual(today))
			return;

		BigDecimal remaining = absDiff.setScale(1, RoundingMode.HALF_UP);

		if (diff.compareTo(BigDecimal.ZERO) > 0) {
			items.add(new FeedbackItem(FeedbackType.DAILY_COMPLETE, FeedbackLevel.LV2, "目標体重まであと" + remaining + "kgです",
					"現在の体重は" + current + "kgです。目標体重" + target + "kgに向けて、無理のないペースで取り組みましょう。", latest.getMeasuredAt(),
					"trending-down"));
		} else {
			items.add(new FeedbackItem(FeedbackType.DAILY_COMPLETE, FeedbackLevel.LV2, "目標体重まであと" + remaining + "kgです",
					"現在の体重は" + current + "kgです。目標体重" + target + "kgに向けて、バランスのよい食事と適度な運動を心がけましょう。",
					latest.getMeasuredAt(), "trending-up"));
		}
	}

	private void checkWeightChange(List<Weight> logs, List<FeedbackItem> items) {
		if (logs.size() < 2)
			return;
		Weight latest = logs.get(0);
		Weight previous = logs.get(1);
		BigDecimal current = latest.getWeight();
		BigDecimal before = previous.getWeight();
		if (current == null || before == null || before.compareTo(BigDecimal.ZERO) == 0)
			return;

		BigDecimal diff = current.subtract(before);
		BigDecimal percent = diff.divide(before, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).abs();
		long days = ChronoUnit.DAYS.between(previous.getRecordedDate(), latest.getRecordedDate());
		boolean suddenDaily = days <= 1 && diff.abs().compareTo(LV4_DAILY_CHANGE_KG) >= 0;

		String diffText = (diff.compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + diff + "kg";

		if (percent.compareTo(LV4_CHANGE_PERCENT) >= 0 || suddenDaily) {
			items.add(new FeedbackItem(FeedbackType.WEIGHT_SUDDEN_CHANGE, FeedbackLevel.LV4, "急激な体重変化があります",
					"前回比で" + diffText + "の変化がありました。健康状態と入力内容を確認してください。", latest.getRecordedDate().atStartOfDay(),
					"alert-octagon"));
			return;
		}

		if (percent.compareTo(LV3_CHANGE_PERCENT) >= 0) {
			items.add(new FeedbackItem(FeedbackType.WEIGHT_BIG_CHANGE, FeedbackLevel.LV3, "体重変化が大きいです",
					"前回比で" + diffText + "の変化がありました。入力内容を確認してください。", latest.getRecordedDate().atStartOfDay(),
					"alert-triangle"));
		}
	}

	private FeedbackItem buildNoRecordReminder(LocalDate today, String title, String message) {
		return new FeedbackItem(FeedbackType.WEIGHT_NO_RECORD, FeedbackLevel.LV0, title, message, today.atStartOfDay(),
				"calendar-x");
	}

	private List<Weight> latestPerDateDesc(List<Weight> logs) {
		Map<LocalDate, Weight> map = new HashMap<>();
		for (Weight weight : logs) {
			map.merge(weight.getRecordedDate(), weight,
					(oldWeight, newWeight) -> oldWeight.getId() > newWeight.getId() ? oldWeight : newWeight);
		}
		return map.values().stream().sorted(Comparator.comparing(Weight::getRecordedDate).reversed()).toList();
	}
}