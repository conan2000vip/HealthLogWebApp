package com.healthlog.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;
import com.healthlog.app.service.feedbackservice.model.FeedbackLevel;

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

	private static final int SLEEP_TOLERANCE_MINUTES = 30; // 睡眠: 30分
	private static final int STEP_TOLERANCE_COUNT = 300; // 歩数: 300歩
	private static final int STEP_TOLERANCE_PERCENT = 95; // 歩数: 95%
	private static final int WATER_TOLERANCE_ML = 100; // 水分: 100ml
	private static final int WATER_TOLERANCE_PERCENT = 95; // 水分: 95%
	private static final BigDecimal WEIGHT_TOLERANCE_KG = BigDecimal.valueOf(0.5); // 体重: 0.5kg

	private static final java.util.Map<com.healthlog.app.service.feedbackservice.model.FeedbackType, String> SEVERE_LABELS = java.util.Map
			.of(com.healthlog.app.service.feedbackservice.model.FeedbackType.WEIGHT_SUDDEN_CHANGE, "体重変化大",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.WEIGHT_BIG_CHANGE, "体重変化大",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.SLEEP_CONTINUOUS_SHORT, "睡眠不足が継続",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.SLEEP_SHORT, "睡眠不足",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.WATER_EXCESS, "水分過剰",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.WATER_LOW, "水分不足",
					com.healthlog.app.service.feedbackservice.model.FeedbackType.STEP_LOW, "歩数不足");

	// =========================================================
	// ホーム画面のデータを取得
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
	// 連続記録日数（ストリーク）の計算
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
	// 今日の健康データをまとめる
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
	// 体重 (最新の記録のみを取得)
	// =========================================================
	private void applyWeightToday(Map<String, Object> today, Profile profile, Long profileId, Long currentUserId,
			LocalDate todayDate) {
		try {
			Map<String, Object> result = weightService.list(profileId, currentUserId, todayDate, todayDate, 0);
			List<?> logs = (List<?>) result.get("logs");

			if (logs == null || logs.isEmpty()) {
				today.put("weightGoalStatus", "NO_RECORD");
				today.put("weightGoalText", "今日の体重は未記録です");
				return;
			}

			// 1日の最新の体重記録を検索する (measuredAtで比較)
			Weight latestWeight = null;
			for (Object obj : logs) {
				if (obj instanceof Weight w) {
					if (latestWeight == null || w.getMeasuredAt().compareTo(latestWeight.getMeasuredAt()) > 0) {
						latestWeight = w;
					}
				}
			}

			if (latestWeight == null || latestWeight.getWeight() == null) {
				today.put("weightGoalStatus", "NO_RECORD");
				today.put("weightGoalText", "今日の体重は未記録です");
				return;
			}
			today.put("weightKg", latestWeight.getWeight());

			BigDecimal bmi = latestWeight.getBmi();
			today.put("bmi", bmi);
			if (bmi != null) {
				String[] statusInfo = bmiStatusOf(bmi);
				today.put("bmiStatus", statusInfo[0]); // ví dụ: "肥満(2度以上)"
				today.put("bmiStatusCode", statusInfo[1]); // ví dụ: "obese"
			}

			BigDecimal targetWeight = profile.getTargetWeight();
			if (targetWeight == null || targetWeight.compareTo(BigDecimal.ZERO) <= 0) {
				today.put("weightGoalStatus", "NO_GOAL");
				today.put("weightGoalText", "目標体重が設定されていません");
				return;
			}

			today.put("targetWeight", targetWeight);
			BigDecimal difference = latestWeight.getWeight().subtract(targetWeight);
			if (difference.abs().compareTo(WEIGHT_TOLERANCE_KG) <= 0) {
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

	private String[] bmiStatusOf(BigDecimal bmi) {
		if (bmi == null) {
			return new String[] { null, null };
		}
		if (bmi.compareTo(BigDecimal.valueOf(18.5)) < 0) {
			return new String[] { "低体重", "underweight" };
		}
		if (bmi.compareTo(BigDecimal.valueOf(25.0)) < 0) {
			return new String[] { "普通体重", "normal" };
		}
		if (bmi.compareTo(BigDecimal.valueOf(30.0)) < 0) {
			return new String[] { "肥満(1度)", "warning" };
		}
		return new String[] { "肥満(2度以上)", "obese" };
	}

	// =========================================================
	// 睡眠 (1日の合計時間を計算)
	// =========================================================
	private void applySleepToday(Map<String, Object> today, Profile profile, Long profileId, Long currentUserId,
			LocalDate todayDate) {
		try {
			Map<String, Object> result = sleepService.list(profileId, currentUserId, todayDate, todayDate, 0);
			List<?> logs = (List<?>) result.get("logs");

			if (logs == null || logs.isEmpty()) {
				return;
			}

			// 1日の睡眠時間をすべて合計する
			int totalDurationMinutes = 0;
			boolean hasValidRecord = false;

			for (Object obj : logs) {
				if (obj instanceof Sleep s && s.getSleepMinutes() != null) {
					totalDurationMinutes += s.getSleepMinutes();
					hasValidRecord = true;
				}
			}

			if (!hasValidRecord) {
				return;
			}

			int hours = totalDurationMinutes / 60;
			int minutes = totalDurationMinutes % 60;
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
			if (totalDurationMinutes >= (goalMinutes - SLEEP_TOLERANCE_MINUTES)) {
				today.put("sleepGoalStatus", "ACHIEVED");
				today.put("sleepGoalText", "睡眠目標を達成しました");
				return;
			}

			int remainingMinutes = goalMinutes - totalDurationMinutes;
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
	// 水分
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

			if (total >= goal || percent >= WATER_TOLERANCE_PERCENT || (goal - total) <= WATER_TOLERANCE_ML) {
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
	// 歩数
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

			if (steps >= goal || percent >= STEP_TOLERANCE_PERCENT || (goal - steps) <= STEP_TOLERANCE_COUNT) {
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
	// 家族の健康サマリー (簡略化ラベル実装)
	// =========================================================
	private List<Map<String, Object>> buildFamilyMembers(List<Profile> profiles, Long currentUserId, LocalDate today) {
		List<Map<String, Object>> members = new ArrayList<>();

		for (Profile profile : profiles) {
			Map<String, Object> familyToday = buildToday(profile.getId(), currentUserId);

			boolean hasWeight = !"NO_RECORD".equals(familyToday.get("weightGoalStatus"));
			boolean hasSleep = !"NO_RECORD".equals(familyToday.get("sleepGoalStatus"));
			boolean hasWater = !"NO_RECORD".equals(familyToday.get("waterGoalStatus"));
			boolean hasStep = !"NO_RECORD".equals(familyToday.get("stepGoalStatus"));

			int totalItems = 4;
			int doneItems = (hasWeight ? 1 : 0) + (hasSleep ? 1 : 0) + (hasWater ? 1 : 0) + (hasStep ? 1 : 0);

			LocalDate historyFrom = LocalDate.of(2000, 1, 1);
			LocalDate latestRecordedDate = getLatestRecordedDate(profile.getId(), currentUserId, historyFrom, today);
			boolean hasAnyHistoricalData = latestRecordedDate != null;

			List<String> severeFeedbackLabels = new ArrayList<>();
			boolean hasLv4 = extractHealthTrendFeedback(profile.getId(), severeFeedbackLabels);

			List<String> missingItems = new ArrayList<>();
			if (!hasWeight)
				missingItems.add("体重");
			if (!hasSleep)
				missingItems.add("睡眠");
			if (!hasWater)
				missingItems.add("水分");
			if (!hasStep)
				missingItems.add("歩数");

			List<String> allIssues = new ArrayList<>();

			allIssues.addAll(severeFeedbackLabels);

			if (hasAnyHistoricalData && ChronoUnit.DAYS.between(latestRecordedDate, today) >= 3) {
				allIssues.add(ChronoUnit.DAYS.between(latestRecordedDate, today) + "日未記録");
			}

			for (String missing : missingItems) {
				allIssues.add(missing + "未入力");
			}

			String status;
			String statusLabel;

			if (!hasAnyHistoricalData) {
				status = "NO_RECORD";
				statusLabel = "未記録";
			} else if (allIssues.isEmpty()) {
				status = "OK";
				statusLabel = "記録順調";
			} else {
				boolean isPrimaryDanger = hasLv4 || !severeFeedbackLabels.isEmpty()
						|| (hasAnyHistoricalData && ChronoUnit.DAYS.between(latestRecordedDate, today) >= 3);
				status = isPrimaryDanger ? "DANGER" : "WARN";

				if (allIssues.size() == 1) {
					statusLabel = allIssues.get(0);
				} else {
					statusLabel = allIssues.get(0) + " ほか" + (allIssues.size() - 1) + "件";
				}
			}

			String updatedTime = getFamilyUpdatedTime(profile.getId(), currentUserId, today);
			Map<String, Object> member = new HashMap<>();
			member.put("id", profile.getId());
			member.put("name", profile.getName());
			member.put("age", profile.getAge());
			member.put("avatar", profile.getAvatar());
			member.put("status", status);
			member.put("statusLabel", statusLabel);
			member.put("doneItems", doneItems);
			member.put("totalItems", totalItems);
			member.put("updatedTime", updatedTime);
			member.put("relationship", profile.getRelationship());
			member.put("isPrimary", profile.getIsPrimary());
			members.add(member);
		}
		return members;
	}

	private boolean extractHealthTrendFeedback(Long profileId, List<String> attentionItems) {
		List<FeedbackItem> allItems = new ArrayList<>();
		allItems.addAll(feedbackService.getWeightFeedback(profileId));
		allItems.addAll(feedbackService.getSleepFeedback(profileId));
		allItems.addAll(feedbackService.getWaterFeedback(profileId));
		allItems.addAll(feedbackService.getStepFeedback(profileId));

		boolean hasLv4 = false;
		List<FeedbackItem> severe = allItems.stream().filter(
				item -> item.getLevel() != null && item.getLevel().getPriority() >= FeedbackLevel.LV3.getPriority())
				.sorted(Comparator.comparingInt((FeedbackItem i) -> i.getLevel().getPriority()).reversed()).toList();

		for (FeedbackItem item : severe) {
			if (item.getLevel() == FeedbackLevel.LV4) {
				hasLv4 = true;
			}
			String label = SEVERE_LABELS.getOrDefault(item.getType(), item.getTitle());
			if (!attentionItems.contains(label)) {
				attentionItems.add(label);
			}
		}
		return hasLv4;
	}

	private Map<String, Object> buildFamilySummary(List<Map<String, Object>> familyMembers) {
		long attentionCount = familyMembers.stream().filter(member -> !"OK".equals(member.get("status"))).count();

		Map<String, Object> summary = new HashMap<>();
		summary.put("attentionCount", (int) attentionCount);
		summary.put("totalCount", familyMembers.size());

		return summary;
	}

	// =========================================================
	// 家族データの最終更新時間
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