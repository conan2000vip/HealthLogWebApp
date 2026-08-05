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

import com.healthlog.app.entity.Weight;
import com.healthlog.app.repository.WeightRepository;
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;
import com.healthlog.app.service.feedbackservice.model.FeedbackLevel;
import com.healthlog.app.service.feedbackservice.model.FeedbackType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WeightFeedbackRule {

	private final WeightRepository weightRepository;

	private static final BigDecimal LV3_CHANGE_PERCENT = BigDecimal.valueOf(3);
	private static final BigDecimal LV4_CHANGE_PERCENT = BigDecimal.valueOf(5);
	private static final BigDecimal LV4_DAILY_CHANGE_KG = BigDecimal.valueOf(2);
	private static final long NO_RECORD_DAYS_THRESHOLD = 3;

	public List<FeedbackItem> evaluate(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();
		List<Weight> logs = weightRepository.findByProfile_IdOrderByRecordedDateDesc(profileId);
		if (logs.isEmpty()) {
			return items;
		}

		List<Weight> latestLogs = latestPerDateDesc(logs);

		checkAchievementToday(latestLogs, items);
		checkNoRecord(latestLogs, items);
		checkWeightChange(latestLogs, items);
		return items;
	}

	// ---- Lv1: 達成（今日の記録完了） ----
	private void checkAchievementToday(
			List<Weight> logs,
			List<FeedbackItem> items) {
		LocalDate today = LocalDate.now();
		Weight latest = logs.get(0);
		if (latest.getRecordedDate().isEqual(today)) {
			items.add(new FeedbackItem(
					FeedbackType.DAILY_COMPLETE,
					FeedbackLevel.LV1,
					"今日の健康記録を完了しました",
					"体重の記録、お疲れさまでした！",
					latest.getMeasuredAt(),
					"check-circle"));
		}
	}

	// ---- Lv2: 体重記録なし（3日間） ----
	private void checkNoRecord(
			List<Weight> logs,
			List<FeedbackItem> items) {
		LocalDate today = LocalDate.now();
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
		if (before == null || before.compareTo(BigDecimal.ZERO) == 0) {
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