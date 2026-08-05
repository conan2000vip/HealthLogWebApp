package com.healthlog.app.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.healthlog.app.service.feedbackservice.WeightFeedbackRule;
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeedbackService {

	private final WeightFeedbackRule weightFeedbackRule;

	/**
	 * 画面Weight：Weightのフィードバックのみ取得（全件、優先度順）
	 * 表示件数の制限（「すべて見る」対応）は画面側で行う。
	 */
	public List<FeedbackItem> getWeightFeedback(Long profileId) {
		return sortByPriority(weightFeedbackRule.evaluate(profileId));
	}

	/**
	 * Dashboard Home
	 * 将来的にWeight + Sleep + Water + Stepをまとめる
	 */
	public List<FeedbackItem> getHomeFeedback(Long profileId) {
		List<FeedbackItem> items = new ArrayList<>();
		items.addAll(weightFeedbackRule.evaluate(profileId));
		return sortByPriority(items);
	}

	// 優先順位: Lv4 > Lv3 > Lv2 > Lv1。同一優先度は発生日時が新しい順。
	private List<FeedbackItem> sortByPriority(List<FeedbackItem> items) {
		return items.stream()
				.sorted(
						Comparator.comparingInt((FeedbackItem i) -> i.getLevel().getPriority()).reversed()
								.thenComparing(FeedbackItem::getOccurredAt, Comparator.reverseOrder()))
				.toList();
	}
}