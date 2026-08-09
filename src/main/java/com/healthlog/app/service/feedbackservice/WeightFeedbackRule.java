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
	// TODO: 「目標達成」とみなす許容誤差。仕様に合わせて調整してください
	private static final BigDecimal TARGET_ACHIEVED_TOLERANCE_KG = BigDecimal.valueOf(0.5);

	public List<FeedbackItem> evaluate(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();
		Profile profile = profileRepository.findById(profileId).orElseThrow();
		LocalDate today = LocalDate.now();
		LocalDate yesterday = today.minusDays(1);

		List<Weight> logs = weightRepository.findByProfile_IdOrderByRecordedDateDesc(profileId);

		// ---- お知らせ: 記録が一度もない ----
		if (logs.isEmpty()) {
			items.add(buildNoRecordReminder(today, "体重記録がありません", "まだ体重データが記録されていません。記録を始めてみましょう。"));
			return items;
		}

		List<Weight> latestLogs = latestPerDateDesc(logs);
		boolean hasToday = latestLogs.stream().anyMatch(w -> w.getRecordedDate().isEqual(today));
		boolean hasYesterday = latestLogs.stream().anyMatch(w -> w.getRecordedDate().isEqual(yesterday));

		// ---- お知らせ: 今日/昨日の記録漏れ（体重は毎日でなくてもよいため、評価は継続する） ----
		if (!hasToday) {
			items.add(
					buildNoRecordReminder(today, "今日の体重記録がありません", "今日の体重データがまだ記録されていません。記録すると、あなたに合ったフィードバックが受け取れます。"));
		}
		if (!hasYesterday) {
			items.add(buildNoRecordReminder(today, "昨日の体重記録がありません", "昨日分の体重データが記録されていません。忘れずに記録しましょう。"));
		}

		// ---- メイン評価: LV4/LV3（変化）> LV2（3日以上未記録）> LV1（達成）、互いに排他 ----
		List<FeedbackItem> mainFeedback = new ArrayList<>();
		checkWeightChange(latestLogs, mainFeedback); // LV4 / LV3
		if (mainFeedback.isEmpty()) {
			checkNoRecord(latestLogs, today, mainFeedback); // LV2
		}
		if (mainFeedback.isEmpty()) {
			checkAchievement(latestLogs, profile, today, mainFeedback); // LV1
		}

		items.addAll(mainFeedback);
		return items;
	}

	// ---- Lv1: 目標体重達成、または（目標未設定 or 未達成時）今日の記録完了 ----
	private void checkAchievement(
			List<Weight> logs,
			Profile profile,
			LocalDate today,
			List<FeedbackItem> items) {
		Weight latest = logs.get(0);
		BigDecimal target = profile.getTargetWeight();
		boolean hasGoal = target != null && target.compareTo(BigDecimal.ZERO) > 0;

		if (hasGoal && latest.getWeight() != null) {
			BigDecimal diff = latest.getWeight().subtract(target).abs();
			if (diff.compareTo(TARGET_ACHIEVED_TOLERANCE_KG) <= 0) {
				items.add(new FeedbackItem(
						FeedbackType.WEIGHT_GOAL_ACHIEVED,
						FeedbackLevel.LV1,
						"目標体重を達成しました",
						"現在の体重は" + latest.getWeight() + "kgです。設定した目標体重を達成しました！",
						latest.getMeasuredAt(),
						"check-circle"));
				return;
			}
		}

		if (!latest.getRecordedDate().isEqual(today)) {
			return;
		}
		String message = hasGoal
				? "体重の記録、お疲れさまでした！目標体重に向けて引き続き頑張りましょう。"
				: "体重の記録、お疲れさまでした！目標体重を設定すると、より詳しいフィードバックが受け取れます。";
		items.add(new FeedbackItem(
				FeedbackType.DAILY_COMPLETE,
				FeedbackLevel.LV1,
				"今日の健康記録を完了しました",
				message,
				latest.getMeasuredAt(),
				"check-circle"));
	}

	// ---- Lv2: 体重記録なし（3日間） ----
	private void checkNoRecord(
			List<Weight> logs,
			LocalDate today,
			List<FeedbackItem> items) {
		Weight latest = logs.get(0);
		long days = ChronoUnit.DAYS.between(latest.getRecordedDate(), today);
		if (days >= NO_RECORD_DAYS_THRESHOLD) {
			items.add(new FeedbackItem(
					FeedbackType.WEIGHT_NO_RECORD,
					FeedbackLevel.LV2,
					"最近、体重記録がありません",
					"今日の状態を記録してみましょう。",
					latest.getMeasuredAt(),
					"lightbulb"));
		}
	}

	// ---- Lv3 / Lv4: 前回比の変化 ----
	private void checkWeightChange(
			List<Weight> logs,
			List<FeedbackItem> items) {
		if (logs.size() < 2) {
			return;
		}
		Weight latest = logs.get(0);
		Weight previous = logs.get(1);
		BigDecimal current = latest.getWeight();
		BigDecimal before = previous.getWeight();
		if (current == null || before == null || before.compareTo(BigDecimal.ZERO) == 0) {
			return;
		}

		BigDecimal diff = current.subtract(before);
		BigDecimal percent = diff.divide(before, 4, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100)).abs();
		long days = ChronoUnit.DAYS.between(previous.getRecordedDate(), latest.getRecordedDate());
		boolean suddenDaily = days <= 1 && diff.abs().compareTo(LV4_DAILY_CHANGE_KG) >= 0;

		String diffText = (diff.compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + diff + "kg";

		if (percent.compareTo(LV4_CHANGE_PERCENT) >= 0 || suddenDaily) {
			items.add(new FeedbackItem(
					FeedbackType.WEIGHT_SUDDEN_CHANGE,
					FeedbackLevel.LV4,
					"急激な体重変化があります",
					"前回比で" + diffText + "の変化がありました。健康状態と入力内容を確認してください。",
					latest.getRecordedDate().atStartOfDay(),
					"alert-octagon"));
			return;
		}

		if (percent.compareTo(LV3_CHANGE_PERCENT) >= 0) {
			items.add(new FeedbackItem(
					FeedbackType.WEIGHT_BIG_CHANGE,
					FeedbackLevel.LV3,
					"体重変化が大きいです",
					"前回比で" + diffText + "の変化がありました。入力内容を確認してください。",
					latest.getRecordedDate().atStartOfDay(),
					"alert-triangle"));
		}
	}

	private FeedbackItem buildNoRecordReminder(LocalDate today, String title, String message) {
		return new FeedbackItem(FeedbackType.WEIGHT_NO_RECORD, FeedbackLevel.LV0, title, message,
				today.atStartOfDay(), "calendar-x");
	}

	private List<Weight> latestPerDateDesc(List<Weight> logs) {
		Map<LocalDate, Weight> map = new HashMap<>();
		for (Weight weight : logs) {
			map.merge(weight.getRecordedDate(), weight,
					(oldWeight, newWeight) -> oldWeight.getId() > newWeight.getId() ? oldWeight : newWeight);
		}
		return map.values().stream()
				.sorted(Comparator.comparing(Weight::getRecordedDate).reversed())
				.toList();
	}
}