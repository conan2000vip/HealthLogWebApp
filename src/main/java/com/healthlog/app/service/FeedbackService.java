package com.healthlog.app.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.healthlog.app.service.feedbackservice.SleepFeedbackRule;
import com.healthlog.app.service.feedbackservice.StepFeedbackRule;
import com.healthlog.app.service.feedbackservice.WaterFeedbackRule;
import com.healthlog.app.service.feedbackservice.WeightFeedbackRule;
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeedbackService {
	private final WeightFeedbackRule weightFeedbackRule;
	private final SleepFeedbackRule sleepFeedbackRule;
	private final WaterFeedbackRule waterFeedbackRule;
	private final StepFeedbackRule stepFeedbackRule;

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
		return sortByPriority(items);
	}

	private List<FeedbackItem> sortByPriority(List<FeedbackItem> items) {
		return items.stream().sorted(
				Comparator.comparingInt((FeedbackItem i) -> i.getLevel().getPriority()).reversed()
						.thenComparing(FeedbackItem::getOccurredAt, Comparator.reverseOrder()))
				.toList();
	}
}