package com.healthlog.app.service.feedbackservice.model;

import java.time.LocalDateTime;

import lombok.Getter;

@Getter
public class FeedbackItem {
	private final FeedbackType type;
	private final FeedbackLevel level;
	private final String title;
	private final String message;
	private final LocalDateTime occurredAt;
	private final String icon;

	public FeedbackItem(
			FeedbackType type,
			FeedbackLevel level,
			String title,
			String message,
			LocalDateTime occurredAt,
			String icon) {
		this.type = type;
		this.level = level;
		this.title = title;
		this.message = message;
		this.occurredAt = occurredAt;
		this.icon = icon;
	}
}