package com.healthlog.app.service.feedbackservice.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedbackLevel {
	LV0(0, "お知らせ", "lv0"), LV1(1, "達成", "lv1"), LV2(2, "アドバイス", "lv2"), LV3(3, "注意", "lv3"), LV4(4, "重要", "lv4");

	private final int priority;
	private final String label;
	private final String cssClass;
}