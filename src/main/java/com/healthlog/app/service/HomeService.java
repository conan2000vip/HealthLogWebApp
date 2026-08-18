package com.healthlog.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.Sleep;
import com.healthlog.app.entity.Step;
import com.healthlog.app.entity.Water;
import com.healthlog.app.entity.Weight;
import com.healthlog.app.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HomeService {

	private final ProfileService profileService;
	private final WeightService weightService;
	private final SleepService sleepService;
	private final WaterService waterService;
	private final StepService stepService;
	private final FeedbackService feedbackService;
	private final MemoService memoService;

	private static final java.util.Map<com.healthlog.app.service.feedbackservice.model.FeedbackType, String> SEVERE_LABELS = java.util.Map
			.of(com.healthlog.app.service.feedbackservice.model.FeedbackType.WEIGHT_SUDDEN_CHANGE, "体重変化大",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.WEIGHT_BIG_CHANGE, "体重変化大",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.SLEEP_CONTINUOUS_SHORT, "睡眠不足が継続",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.SLEEP_SHORT, "睡眠不足",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.WATER_EXCESS, "水分過剰",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.WATER_LOW, "水分不足",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.STEP_LOW, "歩数不足");

	// =========================================================
	// Home
	// =========================================================
	public Map<String, Object> getHomeData(Long profileId, Long currentUserId) {
		LocalDate today = LocalDate.now();

		Profile currentProfile = profileService.getProfile(currentUserId, profileId);
		List<Profile> profileList = profileService.getProfiles(currentUserId);

		List<Profile> otherProfiles = profileList.stream()
				.filter(profile -> !profile.getId().equals(currentProfile.getId())).toList();

		List<Map<String, Object>> familyMembers = buildFamilyMembers(otherProfiles, currentUserId, today);

		Map<String, Object> data = new HashMap<>();
		data.put("currentProfile", currentProfile);
		data.put("goals", currentProfile);
		data.put("profileList", profileList);
		data.put("today", buildToday(profileId, currentUserId));
		data.put("recentMemos", memoService.getRecentThreeDays(profileId, currentUserId));
		data.put("familyMembers", familyMembers);
		data.put("familySummary", buildFamilySummary(familyMembers));
		data.put("feedbackList", feedbackService.getHomeFeedback(profileId));
		data.put("currentStreak", getCurrentStreak(profileId, currentUserId, today));

		return data;
	}

	// =========================================================
	// Health Streak
	// =========================================================

	private int getCurrentStreak(Long profileId, Long currentUserId, LocalDate today) {
		Set<LocalDate> weightDates = new HashSet<>();
		Set<LocalDate> sleepDates = new HashSet<>();
		Set<LocalDate> waterDates = new HashSet<>();
		Set<LocalDate> stepDates = new HashSet<>();

		try {
			Map<String, Object> result = weightService.list(profileId, currentUserId, LocalDate.of(2000, 1, 1), today,
					0);
			List<?> logs = (List<?>) result.get("logs");
			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Weight w && w.getRecordedDate() != null) {
						weightDates.add(w.getRecordedDate());
					}
				}
			}
		} catch (BusinessException e) {
		}

		try {
			Map<String, Object> result = sleepService.list(profileId, currentUserId, LocalDate.of(2000, 1, 1), today,
					0);
			List<?> logs = (List<?>) result.get("logs");
			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Sleep s && s.getRecordedDate() != null) {
						sleepDates.add(s.getRecordedDate());
					}
				}
			}
		} catch (BusinessException e) {
		}

		try {
			Map<String, Object> result = waterService.list(profileId, currentUserId, LocalDate.of(2000, 1, 1), today,
					0);
			List<?> logs = (List<?>) result.get("logs");
			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Water w && w.getRecordedDate() != null) {
						waterDates.add(w.getRecordedDate());
					}
				}
			}
		} catch (BusinessException e) {
		}

		try {
			Map<String, Object> result = stepService.list(profileId, currentUserId, LocalDate.of(2000, 1, 1), today, 0);
			List<?> logs = (List<?>) result.get("logs");
			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Step s && s.getRecordedDate() != null) {
						stepDates.add(s.getRecordedDate());
					}
				}
			}
		} catch (BusinessException e) {
		}

		// 4種類すべてが記録された日だけを残す（AND条件）
		Set<LocalDate> recordedDates = new HashSet<>(weightDates);
		recordedDates.retainAll(sleepDates);
		recordedDates.retainAll(waterDates);
		recordedDates.retainAll(stepDates);
		LocalDate startPoint;

		if (recordedDates.contains(today)) {
			startPoint = today;
		} else if (recordedDates.contains(today.minusDays(1))) {
			startPoint = today.minusDays(1);
		} else {
			return 0;
		}

		int streak = 0;
		LocalDate checkDate = startPoint;
		while (recordedDates.contains(checkDate)) {
			streak++;
			checkDate = checkDate.minusDays(1);
		}
		return streak;
	}

	// =========================================================
	// Today's health data
	// =========================================================
	private Map<String, Object> buildToday(Long profileId, Long currentUserId) {
		LocalDate todayDate = LocalDate.now();
		Profile profile = profileService.getProfile(currentUserId, profileId);

		Map<String, Object> today = createTodayDefaults();

		applyWeightToday(today, profile, profileId, currentUserId, todayDate);
		applySleepToday(today, profile, profileId, currentUserId, todayDate);
		applyWaterToday(today, profile, profileId, currentUserId, todayDate);
		applyStepToday(today, profile, profileId, currentUserId, todayDate);

		return today;
	}

	private Map<String, Object> createTodayDefaults() {
		Map<String, Object> today = new HashMap<>();

		today.put("weightKg", null);
		today.put("bmi", null);
		today.put("targetWeight", null);
		today.put("weightGoalStatus", "NO_RECORD");
		today.put("weightGoalText", "今日の体重は未記録です");

		today.put("sleepHour", null);
		today.put("sleepMinute", null);
		today.put("sleepGoalMinutes", null);
		today.put("sleepGoalStatus", "NO_RECORD");
		today.put("sleepGoalText", "今日の睡眠は未記録です");

		today.put("waterMl", null);
		today.put("waterPercent", 0);
		today.put("waterGoalStatus", "NO_RECORD");
		today.put("waterGoalText", "今日の水分は未記録です");

		today.put("stepCount", null);
		today.put("stepPercent", 0);
		today.put("stepGoalStatus", "NO_RECORD");
		today.put("stepGoalText", "今日の歩数は未記録です");

		return today;
	}

	// =========================================================
	// Weight
	// =========================================================
	private void applyWeightToday(Map<String, Object> today, Profile profile, Long profileId, Long currentUserId,
			LocalDate todayDate) {
		try {
			Map<String, Object> result = weightService.list(profileId, currentUserId, todayDate, todayDate, 0);
			List<?> logs = (List<?>) result.get("logs");

			if (logs == null || logs.isEmpty()) {
				return;
			}

			Object weight = logs.get(0);

			if (!(weight instanceof Weight w)) {
				return;
			}

			today.put("weightKg", w.getWeight());
			today.put("bmi", w.getBmi());

			BigDecimal targetWeight = profile.getTargetWeight();

			if (targetWeight == null || targetWeight.compareTo(BigDecimal.ZERO) <= 0 || w.getWeight() == null) {
				today.put("weightGoalStatus", "NO_GOAL");
				today.put("weightGoalText", "目標体重が設定されていません");
				return;
			}

			today.put("targetWeight", targetWeight);

			BigDecimal difference = w.getWeight().subtract(targetWeight);

			if (difference.abs().compareTo(BigDecimal.valueOf(0.5)) <= 0) {
				today.put("weightGoalStatus", "ACHIEVED");
				today.put("weightGoalText", "目標体重を達成しました");
				return;
			}

			BigDecimal remaining = difference.abs().setScale(1, RoundingMode.HALF_UP);

			if (difference.compareTo(BigDecimal.ZERO) > 0) {
				today.put("weightGoalStatus", "LOSE");
			} else {
				today.put("weightGoalStatus", "GAIN");
			}

			today.put("weightGoalText", "目標まであと" + remaining + " kg");

		} catch (BusinessException e) {
			today.put("weightGoalStatus", "NO_RECORD");
			today.put("weightGoalText", "今日の体重は未記録です");
		}
	}

	// =========================================================
	// Sleep
	// =========================================================
	private void applySleepToday(Map<String, Object> today, Profile profile, Long profileId, Long currentUserId,
			LocalDate todayDate) {
		try {
			Map<String, Object> result = sleepService.list(profileId, currentUserId, todayDate, todayDate, 0);
			List<?> logs = (List<?>) result.get("logs");

			if (logs == null || logs.isEmpty()) {
				return;
			}

			Object sleep = logs.get(0);

			if (!(sleep instanceof Sleep s) || s.getSleepMinutes() == null) {
				return;
			}

			Integer durationMinutes = s.getSleepMinutes();

			int hours = durationMinutes / 60;
			int minutes = durationMinutes % 60;

			today.put("sleepHour", hours);
			today.put("sleepMinute", minutes);

			BigDecimal sleepGoalHours = profile.getSleepGoalHours();

			if (sleepGoalHours == null || sleepGoalHours.compareTo(BigDecimal.ZERO) <= 0) {
				today.put("sleepGoalStatus", "NO_GOAL");
				today.put("sleepGoalText", "睡眠の目標が設定されていません");
				return;
			}

			int goalMinutes = sleepGoalHours.multiply(BigDecimal.valueOf(60)).intValue();

			today.put("sleepGoalMinutes", goalMinutes);

			if (durationMinutes >= goalMinutes) {
				today.put("sleepGoalStatus", "ACHIEVED");
				today.put("sleepGoalText", "睡眠目標を達成しました");
				return;
			}

			int remainingMinutes = goalMinutes - durationMinutes;
			int remainingHours = remainingMinutes / 60;
			int remainingMins = remainingMinutes % 60;

			today.put("sleepGoalStatus", "NOT_ACHIEVED");

			if (remainingHours > 0) {
				today.put("sleepGoalText", "目標まであと" + remainingHours + "時間" + remainingMins + "分");
			} else {
				today.put("sleepGoalText", "目標まであと" + remainingMins + "分");
			}

		} catch (BusinessException e) {
			today.put("sleepGoalStatus", "NO_RECORD");
			today.put("sleepGoalText", "今日の睡眠は未記録です");
		}
	}

	// =========================================================
	// Water
	// =========================================================
	@SuppressWarnings("unchecked")
	private void applyWaterToday(Map<String, Object> today, Profile profile, Long profileId, Long currentUserId,
			LocalDate todayDate) {
		try {
			Map<String, Object> result = waterService.list(profileId, currentUserId, todayDate, todayDate, 0);
			List<?> logs = (List<?>) result.get("logs");
			Map<String, Object> stats = (Map<String, Object>) result.get("stats");

			if (logs == null || logs.isEmpty()) {
				return;
			}

			Integer total = stats != null ? (Integer) stats.get("todayTotal") : null;
			Integer goal = profile.getWaterGoalMl();

			today.put("waterMl", total);

			if (goal == null || goal <= 0) {
				today.put("waterGoalStatus", "NO_GOAL");
				today.put("waterGoalText", "水分目標が設定されていません");
				return;
			}

			if (total == null) {
				return;
			}

			int percent = (int) Math.round(total * 100.0 / goal);

			today.put("waterPercent", percent);

			if (total >= goal) {
				today.put("waterGoalStatus", "ACHIEVED");
				today.put("waterGoalText", "水分目標を達成しました");
			} else {
				today.put("waterGoalStatus", "NOT_ACHIEVED");
				today.put("waterGoalText", "目標まであと" + (goal - total) + " ml");
			}

		} catch (BusinessException e) {
			today.put("waterGoalStatus", "NO_RECORD");
			today.put("waterGoalText", "今日の水分は未記録です");
		}
	}

	// =========================================================
	// Step
	// =========================================================
	@SuppressWarnings("unchecked")
	private void applyStepToday(Map<String, Object> today, Profile profile, Long profileId, Long currentUserId,
			LocalDate todayDate) {
		try {
			Map<String, Object> result = stepService.list(profileId, currentUserId, todayDate, todayDate, 0);
			List<?> logs = (List<?>) result.get("logs");
			Map<String, Object> stats = (Map<String, Object>) result.get("stats");

			if (logs == null || logs.isEmpty()) {
				return;
			}

			Integer steps = stats != null ? (Integer) stats.get("todaySteps") : null;
			Integer goal = profile.getStepGoal();

			today.put("stepCount", steps);

			if (goal == null || goal <= 0) {
				today.put("stepGoalStatus", "NO_GOAL");
				today.put("stepGoalText", "歩数目標が設定されていません");
				return;
			}

			if (steps == null) {
				return;
			}

			int percent = (int) Math.round(steps * 100.0 / goal);

			today.put("stepPercent", percent);

			if (steps >= goal) {
				today.put("stepGoalStatus", "ACHIEVED");
				today.put("stepGoalText", "歩数目標を達成しました");
			} else {
				today.put("stepGoalStatus", "NOT_ACHIEVED");
				today.put("stepGoalText", "目標まであと" + (goal - steps) + " 歩");
			}

		} catch (BusinessException e) {
			today.put("stepGoalStatus", "NO_RECORD");
			today.put("stepGoalText", "今日の歩数は未記録です");
		}
	}

	// =========================================================
	// Family health summary
	// =========================================================
	private List<Map<String, Object>> buildFamilyMembers(List<Profile> profiles, Long currentUserId, LocalDate today) {
		List<Map<String, Object>> members = new ArrayList<>();

		for (Profile profile : profiles) {
			Map<String, Object> familyToday = buildToday(profile.getId(), currentUserId);

			boolean hasWeightToday = !"NO_RECORD".equals(familyToday.get("weightGoalStatus"));
			boolean hasSleepToday = !"NO_RECORD".equals(familyToday.get("sleepGoalStatus"));
			boolean hasWaterToday = !"NO_RECORD".equals(familyToday.get("waterGoalStatus"));
			boolean hasStepToday = !"NO_RECORD".equals(familyToday.get("stepGoalStatus"));

			int totalItems = 4;
			int doneItems = (hasWeightToday ? 1 : 0) + (hasSleepToday ? 1 : 0) + (hasWaterToday ? 1 : 0)
					+ (hasStepToday ? 1 : 0);

			List<String> attentionItems = new ArrayList<>();
			boolean danger = false;
			LocalDate historyFrom = LocalDate.of(2000, 1, 1);
			LocalDate latestRecordedDate = getLatestRecordedDate(profile.getId(), currentUserId, historyFrom, today);
			boolean hasAnyHistoricalData = latestRecordedDate != null;

			if (!hasAnyHistoricalData) {
				attentionItems.add("まだ健康データがありません");
			} else {
				long noRecordDays = ChronoUnit.DAYS.between(latestRecordedDate, today);

				// ★追加：LV3/LV4の重大フィードバックを最優先で注意事項に入れる
				boolean hasSevereFeedback = addSevereFeedbackAttention(profile.getId(), attentionItems);
				if (hasSevereFeedback) {
					danger = true;
				}

				if (noRecordDays >= 3) {
					attentionItems.add(noRecordDays + "日間記録がありません");
					danger = true;
				}
				addSleepAttention(attentionItems, familyToday, hasSleepToday);
				addWaterAttention(attentionItems, familyToday, hasWaterToday);
				addStepAttention(attentionItems, familyToday, hasStepToday);
				if (!danger && doneItems < totalItems) {
					addMissingRecordAttention(attentionItems, hasWeightToday, hasSleepToday, hasWaterToday,
							hasStepToday);
				}
			}
			if (attentionItems.size() > 2) {
				attentionItems = new ArrayList<>(attentionItems.subList(0, 2));
			}

			String status;
			String statusLabel;
			if (!hasAnyHistoricalData) {
				status = "NO_RECORD";
				statusLabel = "未記録";
			} else if (danger) {
				status = "DANGER";
				statusLabel = "注意";
			} else if (!attentionItems.isEmpty()) {
				status = "WARN";
				statusLabel = "要確認";
			} else {
				status = "OK";
				statusLabel = "良好";
			}

			String updatedTime = getFamilyUpdatedTime(profile.getId(), currentUserId, today);
			Map<String, Object> member = new HashMap<>();
			member.put("id", profile.getId());
			member.put("name", profile.getName());
			member.put("age", profile.getAge());
			member.put("status", status);
			member.put("statusLabel", statusLabel);
			member.put("attentionItems", attentionItems);
			member.put("doneItems", doneItems);
			member.put("totalItems", totalItems);
			member.put("updatedTime", updatedTime);
			members.add(member);
		}
		return members;
	}

	// ★追加：4つのFeedbackRuleからLV3/LV4（重大フィードバック）を検出
	private boolean addSevereFeedbackAttention(Long profileId, List<String> attentionItems) {
		List<com.healthlog.app.service.feedbackservice.model.FeedbackItem> allItems = new ArrayList<>();
		allItems.addAll(feedbackService.getWeightFeedback(profileId));
		allItems.addAll(feedbackService.getSleepFeedback(profileId));
		allItems.addAll(feedbackService.getWaterFeedback(profileId));
		allItems.addAll(feedbackService.getStepFeedback(profileId));

		boolean hasLv4 = false;

		List<com.healthlog.app.service.feedbackservice.model.FeedbackItem> severe = allItems.stream()
				.filter(item -> item.getLevel()
						.getPriority() >= com.healthlog.app.service.feedbackservice.model.FeedbackLevel.LV3
								.getPriority())
				.sorted(java.util.Comparator.comparingInt(
						(com.healthlog.app.service.feedbackservice.model.FeedbackItem i) -> i.getLevel().getPriority())
						.reversed())
				.toList();

		for (com.healthlog.app.service.feedbackservice.model.FeedbackItem item : severe) {
			if (item.getLevel() == com.healthlog.app.service.feedbackservice.model.FeedbackLevel.LV4) {
				hasLv4 = true;
			}
			String label = SEVERE_LABELS.getOrDefault(item.getType(), item.getTitle());
			if (!attentionItems.contains(label)) {
				attentionItems.add(0, label);
			}
		}
		return hasLv4;
	}

	private void addSleepAttention(List<String> attentionItems, Map<String, Object> familyToday,
			boolean hasSleepToday) {
		if (!hasSleepToday) {
			return;
		}

		Integer sleepHour = (Integer) familyToday.get("sleepHour");
		Integer sleepMinute = (Integer) familyToday.get("sleepMinute");

		if (sleepHour == null) {
			return;
		}

		int sleepMinutes = sleepHour * 60 + (sleepMinute != null ? sleepMinute : 0);

		if (sleepMinutes < 300) {
			attentionItems.add("睡眠時間が少なめです");
		} else if ("NOT_ACHIEVED".equals(familyToday.get("sleepGoalStatus"))) {
			attentionItems.add("睡眠目標を下回っています");
		}
	}

	private void addWaterAttention(List<String> attentionItems, Map<String, Object> familyToday,
			boolean hasWaterToday) {
		if (!hasWaterToday || !"NOT_ACHIEVED".equals(familyToday.get("waterGoalStatus"))) {
			return;
		}

		Integer waterPercent = (Integer) familyToday.get("waterPercent");

		if (waterPercent != null && waterPercent < 50) {
			attentionItems.add("水分摂取量が少なめです");
		}
	}

	private void addStepAttention(List<String> attentionItems, Map<String, Object> familyToday, boolean hasStepToday) {
		if (!hasStepToday || !"NOT_ACHIEVED".equals(familyToday.get("stepGoalStatus"))) {
			return;
		}

		Integer stepPercent = (Integer) familyToday.get("stepPercent");

		if (stepPercent != null && stepPercent < 50) {
			attentionItems.add("歩数が少なめです");
		}
	}

	private void addMissingRecordAttention(List<String> attentionItems, boolean hasWeightToday, boolean hasSleepToday,
			boolean hasWaterToday, boolean hasStepToday) {
		List<String> missing = new ArrayList<>();

		if (!hasWeightToday) {
			missing.add("体重");
		}

		if (!hasSleepToday) {
			missing.add("睡眠");
		}

		if (!hasWaterToday) {
			missing.add("水分");
		}

		if (!hasStepToday) {
			missing.add("歩数");
		}

		if (!missing.isEmpty()) {
			attentionItems.add(String.join("・", missing) + "が未入力です");
		}
	}

	private Map<String, Object> buildFamilySummary(List<Map<String, Object>> familyMembers) {
		long attentionCount = familyMembers.stream().filter(member -> !"OK".equals(member.get("status"))).count();

		Map<String, Object> summary = new HashMap<>();
		summary.put("attentionCount", (int) attentionCount);
		summary.put("totalCount", familyMembers.size());

		return summary;
	}

	// =========================================================
	// Family update time
	// =========================================================
	private String getFamilyUpdatedTime(Long profileId, Long currentUserId, LocalDate today) {
		LocalDate from = today.minusDays(30);

		LocalDateTime latestUpdatedAt = getLatestUpdatedAt(profileId, currentUserId, from, today);

		if (latestUpdatedAt == null) {
			return "-";
		}

		if (latestUpdatedAt.toLocalDate().equals(today)) {
			return latestUpdatedAt.format(DateTimeFormatter.ofPattern("HH:mm"));
		}

		return latestUpdatedAt.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
	}

	private LocalDateTime getLatestUpdatedAt(Long profileId, Long currentUserId, LocalDate from, LocalDate to) {
		List<LocalDateTime> updateTimes = new ArrayList<>();

		try {
			Map<String, Object> result = weightService.list(profileId, currentUserId, from, to, 0);
			List<?> logs = (List<?>) result.get("logs");

			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Weight w && w.getUpdatedAt() != null) {
						updateTimes.add(w.getUpdatedAt());
					}
				}
			}
		} catch (BusinessException e) {
		}

		try {
			Map<String, Object> result = sleepService.list(profileId, currentUserId, from, to, 0);
			List<?> logs = (List<?>) result.get("logs");

			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Sleep s && s.getUpdatedAt() != null) {
						updateTimes.add(s.getUpdatedAt());
					}
				}
			}
		} catch (BusinessException e) {
		}

		try {
			Map<String, Object> result = waterService.list(profileId, currentUserId, from, to, 0);
			List<?> logs = (List<?>) result.get("logs");

			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Water w && w.getUpdatedAt() != null) {
						updateTimes.add(w.getUpdatedAt());
					}
				}
			}
		} catch (BusinessException e) {
		}

		try {
			Map<String, Object> result = stepService.list(profileId, currentUserId, from, to, 0);
			List<?> logs = (List<?>) result.get("logs");

			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Step s && s.getUpdatedAt() != null) {
						updateTimes.add(s.getUpdatedAt());
					}
				}
			}
		} catch (BusinessException e) {
		}

		return updateTimes.stream().max(LocalDateTime::compareTo).orElse(null);
	}

	private LocalDate getLatestRecordedDate(Long profileId, Long currentUserId, LocalDate from, LocalDate to) {
		List<LocalDate> recordedDates = new ArrayList<>();
		try {
			Map<String, Object> result = weightService.list(profileId, currentUserId, from, to, 0);
			List<?> logs = (List<?>) result.get("logs");

			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Weight w && w.getRecordedDate() != null) {
						recordedDates.add(w.getRecordedDate());
					}
				}
			}
		} catch (BusinessException e) {
		}
		try {
			Map<String, Object> result = sleepService.list(profileId, currentUserId, from, to, 0);
			List<?> logs = (List<?>) result.get("logs");

			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Sleep s && s.getRecordedDate() != null) {
						recordedDates.add(s.getRecordedDate());
					}
				}
			}
		} catch (BusinessException e) {
		}
		try {
			Map<String, Object> result = waterService.list(profileId, currentUserId, from, to, 0);
			List<?> logs = (List<?>) result.get("logs");
			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Water w && w.getRecordedDate() != null) {
						recordedDates.add(w.getRecordedDate());
					}
				}
			}
		} catch (BusinessException e) {
		}
		try {
			Map<String, Object> result = stepService.list(profileId, currentUserId, from, to, 0);
			List<?> logs = (List<?>) result.get("logs");

			if (logs != null) {
				for (Object log : logs) {
					if (log instanceof Step s && s.getRecordedDate() != null) {
						recordedDates.add(s.getRecordedDate());
					}
				}
			}
		} catch (BusinessException e) {
		}
		return recordedDates.stream().max(LocalDate::compareTo).orElse(null);
	}
}