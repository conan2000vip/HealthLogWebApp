package com.healthlog.app.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.healthlog.app.repository.SleepRepository;
import com.healthlog.app.repository.StepRepository;
import com.healthlog.app.repository.WaterRepository;
import com.healthlog.app.repository.WeightRepository;
import com.healthlog.app.service.feedbackservice.SleepFeedbackRule;
import com.healthlog.app.service.feedbackservice.StepFeedbackRule;
import com.healthlog.app.service.feedbackservice.WaterFeedbackRule;
import com.healthlog.app.service.feedbackservice.WeightFeedbackRule;
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;
import com.healthlog.app.service.feedbackservice.model.FeedbackLevel;
import com.healthlog.app.service.feedbackservice.model.FeedbackType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeedbackService {

	private final WeightFeedbackRule weightFeedbackRule;
	private final SleepFeedbackRule sleepFeedbackRule;
	private final WaterFeedbackRule waterFeedbackRule;
	private final StepFeedbackRule stepFeedbackRule;

	private final WeightRepository weightRepository;
	private final SleepRepository sleepRepository;
	private final WaterRepository waterRepository;
	private final StepRepository stepRepository;

	// 健康記録の連続日数マイルストーン
	private static final int[] HEALTH_STREAK_MILESTONES = { 7, 14, 30, 60, 90, 180, 365 };

	public List<FeedbackItem> getWeightFeedback(Long profileId) {
		return sortByPriority(weightFeedbackRule.evaluate(profileId));
	}

	public List<FeedbackItem> getSleepFeedback(Long profileId) {
		return sortByPriority(sleepFeedbackRule.evaluate(profileId));
	}

	public List<FeedbackItem> getWaterFeedback(Long profileId) {
		return sortByPriority(waterFeedbackRule.evaluate(profileId));
	}

	public List<FeedbackItem> getStepFeedback(Long profileId) {
		return sortByPriority(stepFeedbackRule.evaluate(profileId));
	}

	// Dashboard Home
	public List<FeedbackItem> getHomeFeedback(Long profileId) {

		List<FeedbackItem> items = new ArrayList<>();

		items.addAll(weightFeedbackRule.evaluate(profileId));
		items.addAll(sleepFeedbackRule.evaluate(profileId));
		items.addAll(waterFeedbackRule.evaluate(profileId));
		items.addAll(stepFeedbackRule.evaluate(profileId));

		// 連続健康記録のマイルストーン
		FeedbackItem streakFeedback = checkHealthRecordStreak(profileId);
		if (streakFeedback != null) {
			items.add(streakFeedback);
		}
		return sortByPriority(items);
	}

	// 4種類すべての健康記録が存在する連続日数を計算するWeight Sleep Water Step の4種類すべてが1日でも欠けた時点でStreak終了。
	private int calculateStreak(Long profileId, LocalDate today) {

		int streak = 0;

		for (LocalDate date = today;; date = date.minusDays(1)) {

			boolean hasWeight = weightRepository.existsByProfile_IdAndRecordedDate(profileId, date);

			boolean hasSleep = sleepRepository.existsByProfile_IdAndRecordedDate(profileId, date);

			boolean hasWater = waterRepository.existsByProfile_IdAndRecordedDate(profileId, date);

			boolean hasStep = stepRepository.existsByProfile_IdAndRecordedDate(profileId, date);

			// 4種類すべて必要
			if (!hasWeight || !hasSleep || !hasWater || !hasStep) {
				break;
			}

			streak++;
		}

		return streak;
	}

	// 7 / 14 / 30 / 60 / 90 / 180 / 365日の マイルストーン達成を判定する
	private FeedbackItem checkHealthRecordStreak(Long profileId) {
		LocalDate today = LocalDate.now();
		int streak = calculateStreak(profileId, today);

		// 今日がマイルストーン達成日ではなければ何も表示しない
		if (!isMilestone(streak)) {
			return null;
		}
		return createStreakFeedback(streak, today);
	}

	// マイルストーン判定
	private boolean isMilestone(int streak) {
		for (int milestone : HEALTH_STREAK_MILESTONES) {
			if (streak == milestone) {
				return true;
			}
		}
		return false;
	}

	// Streak達成時のFeedbackを生成
	private FeedbackItem createStreakFeedback(int streak, LocalDate today) {
		String title;
		String message;
		switch (streak) {
		case 7:
			title = "7日間連続で健康記録を続けています！";
			message = "1週間、毎日健康記録を続けることができました！\n素晴らしい習慣の第一歩です。";
			break;
		case 14:
			title = "14日間連続で健康記録を続けています！";
			message = "2週間、毎日健康記録を続けています。\n素晴らしい習慣が身についてきました！";
			break;
		case 30:
			title = "30日間連続で健康記録を続けています！";
			message = "1か月間、健康記録を続けることができました！\n毎日の積み重ねが素晴らしいです。";
			break;
		case 60:
			title = "60日間連続で健康記録を続けています！";
			message = "2か月間、健康記録を続けています。\n継続する力が素晴らしいです！";
			break;
		case 90:
			title = "90日間連続で健康記録を続けています！";
			message = "3か月間、健康記録を続けることができました！\n素晴らしい継続です！";
			break;
		case 180:
			title = "180日間連続で健康記録を続けています！";
			message = "半年間、健康記録を続けています。\n毎日の積み重ねが大きな習慣になっています！";
			break;
		case 365:
			title = "365日間連続で健康記録を続けています！";
			message = "1年間、毎日健康記録を続けることができました！\n本当に素晴らしい継続です！";
			break;
		default:
			return null;
		}
		return new FeedbackItem(FeedbackType.HEALTH_STREAK, FeedbackLevel.LV1, title, message, today.atStartOfDay(),
				"trophy");
	}

	private List<FeedbackItem> sortByPriority(List<FeedbackItem> items) {
		return items.stream().sorted(Comparator.comparingInt((FeedbackItem i) -> i.getLevel().getPriority()).reversed()
				.thenComparing(FeedbackItem::getOccurredAt, Comparator.reverseOrder())).toList();
	}
}