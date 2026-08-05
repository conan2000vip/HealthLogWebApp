package com.healthlog.app.service.feedbackservice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.Water;
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

	private static final int LOW_THRESHOLD_PERCENT = 50;
	private static final int ALMOST_MIN_PERCENT = 80;
	private static final int COMPLETE_PERCENT = 100;

	public List<FeedbackItem> evaluate(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();

		Profile profile = profileRepository.findById(profileId).orElse(null);
		if (profile == null || profile.getWaterGoalMl() == null || profile.getWaterGoalMl() <= 0) {
			return items;
		}

		LocalDate today = LocalDate.now();
		List<Water> todayLogs = waterRepository.findByProfile_IdAndRecordedDate(profileId, today);
		if (todayLogs.isEmpty()) {
			return items; // 今日まだ記録が無い場合は判定しない
		}

		// データが追加されるたびにここで最新の合計を再計算する（累積の実データに基づく）
		int total = todayLogs.stream().mapToInt(w -> w.getAmountMl() == null ? 0 : w.getAmountMl()).sum();
		int goal = profile.getWaterGoalMl();
		int percent = (int) Math.round(total * 100.0 / goal);

		// ---- Lv1: 目標達成 ----
		if (percent >= COMPLETE_PERCENT) {
			items.add(new FeedbackItem(
					FeedbackType.WATER_COMPLETE,
					FeedbackLevel.LV1,
					"今日の水分目標を達成しました",
					"素晴らしいです！引き続き水分補給を心がけましょう。",
					LocalDateTime.now(),
					"check-circle"));
			return items;
		}

		// ---- Lv2: あと◯mlで達成（80%以上100%未満） ----
		if (percent >= ALMOST_MIN_PERCENT) {
			int remaining = goal - total;
			items.add(new FeedbackItem(
					FeedbackType.WATER_ALMOST,
					FeedbackLevel.LV2,
					"もう少しで水分目標を達成できます",
					"あと" + remaining + "mlで今日の水分目標を達成できます。",
					LocalDateTime.now(),
					"droplet"));
			return items;
		}

		// ---- Lv3: 水分量が少ない（50%未満） ----
		if (percent < LOW_THRESHOLD_PERCENT) {
			items.add(new FeedbackItem(
					FeedbackType.WATER_LOW,
					FeedbackLevel.LV3,
					"水分量が少ないようです",
					"こまめに水分補給しましょう。",
					LocalDateTime.now(),
					"alert-triangle"));
		}

		return items;
	}
}