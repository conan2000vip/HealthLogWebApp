package com.healthlog.app.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.healthlog.app.entity.Memo;
import com.healthlog.app.entity.Profile;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.service.AuthService;
import com.healthlog.app.service.FeedbackService;
import com.healthlog.app.service.MemoService;
import com.healthlog.app.service.ProfileService;
import com.healthlog.app.service.SleepService;
import com.healthlog.app.service.StepService;
import com.healthlog.app.service.WaterService;
import com.healthlog.app.service.WeightService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {

	private final AuthService authService;
	private final ProfileService profileService;
	private final WeightService weightService;
	private final SleepService sleepService;
	private final WaterService waterService;
	private final StepService stepService;
	private final FeedbackService feedbackService;
	private final MemoService memoService;

	private List<Memo> getRecentMemos(Long profileId, Long currentUserId) {
		return memoService.getRecentThreeDays(profileId, currentUserId);
	}

	@GetMapping("/profile/{profileId}/home")
	public String home(@PathVariable Long profileId, Model model) {

		LocalDate end = LocalDate.now();
		LocalDate start = end.minusDays(6);

		Long currentUserId = authService.getCurrentUser().getId();
		Profile currentProfile = profileService.getProfile(currentUserId, profileId);

		// ============ プロフィール + 目標 ============
		model.addAttribute("currentProfile", currentProfile);
		model.addAttribute("goals", currentProfile);

		// ============ ヘッダーの「プロファイル切替」ドロップダウン用の一覧 ============
		// header.html 側で th:each="p : ${profileList}" を使うために必要な変数
		List<Profile> profileList = profileService.getProfiles(currentUserId);
		model.addAttribute("profileList", profileList);

		// ============ 本日の4指標 ============
		Map<String, Object> today = buildToday(profileId, currentUserId);
		model.addAttribute("today", today);

		// ============ 健康メモ ============
		model.addAttribute("recentMemos", getRecentMemos(profileId, currentUserId));

		// ============ 家族健康サマリー：上で取得した profileList から現在表示中のプロフィールを除外 ============
		List<Profile> otherProfiles = profileList.stream().filter(p -> !p.getId().equals(currentProfile.getId()))
				.toList();
		List<Map<String, Object>> familyMembers = buildFamilyMembers(otherProfiles, currentUserId, end);
		model.addAttribute("familyMembers", familyMembers);
		model.addAttribute("familySummary", buildFamilySummary(familyMembers));

		// ============ フィードバック（fragment layout/data-controls :: feedbackList）
		// ============
		model.addAttribute("feedbackList", feedbackService.getHomeFeedback(profileId));

		return "home/home";
	}

	private Map<String, Object> buildToday(Long profileId, Long currentUserId) {
		Map<String, Object> today = new HashMap<>();
		LocalDate todayDate = LocalDate.now();

		Profile profile = profileService.getProfile(currentUserId, profileId);

		// =========================================================
		// Weight
		// =========================================================
		try {
			Map<String, Object> result = weightService.list(profileId, currentUserId, todayDate, todayDate, 0);
			List<?> logs = (List<?>) result.get("logs");
			if (logs != null && !logs.isEmpty()) {
				Object weight = logs.get(0);
				if (weight instanceof com.healthlog.app.entity.Weight w) {
					today.put("weightKg", w.getWeight());
					today.put("bmi", w.getBmi());
					BigDecimal targetWeight = profile.getTargetWeight();
					if (targetWeight != null && targetWeight.compareTo(BigDecimal.ZERO) > 0 && w.getWeight() != null) {
						today.put("targetWeight", targetWeight);
						BigDecimal currentWeight = w.getWeight();
						BigDecimal difference = currentWeight.subtract(targetWeight);

						// 目標達成：±0.5kg以内
						if (difference.abs().compareTo(BigDecimal.valueOf(0.5)) <= 0) {
							today.put("weightGoalStatus", "ACHIEVED");
							today.put("weightGoalText", "目標体重を達成しました");

						} else if (difference.compareTo(BigDecimal.ZERO) > 0) {

							// 現在 > 目標
							// → 減量目標
							today.put("weightGoalStatus", "LOSE");
							BigDecimal remaining = difference.setScale(1, RoundingMode.HALF_UP);
							today.put("weightGoalText", "目標まであと" + remaining + " kg");
						} else {

							// 現在 < 目標
							// → 増量目標
							BigDecimal remaining = difference.abs().setScale(1, RoundingMode.HALF_UP);
							today.put("weightGoalStatus", "GAIN");
							today.put("weightGoalText", "目標まであと" + remaining + " kg");
						}
					} else {
						today.put("weightGoalStatus", "NO_GOAL");
						today.put("weightGoalText", "目標体重が設定されていません");
					}
				}
			} else {
				today.put("weightGoalStatus", "NO_RECORD");
				today.put("weightGoalText", "今日の体重は未記録です");
			}
		} catch (BusinessException e) {
			today.put("weightGoalStatus", "NO_RECORD");
			today.put("weightGoalText", "今日の体重は未記録です");
		}

		// =========================================================
		// Sleep
		// =========================================================
		try {
			Map<String, Object> result = sleepService.list(profileId, currentUserId, todayDate, todayDate, 0);
			List<?> logs = (List<?>) result.get("logs");
			if (logs != null && !logs.isEmpty()) {
				Object sleep = logs.get(0);

				if (sleep instanceof com.healthlog.app.entity.Sleep s) {
					Integer durationMinutes = s.getSleepMinutes();

					if (durationMinutes != null) {
						int hours = durationMinutes / 60;
						int minutes = durationMinutes % 60;

						today.put("sleepHour", hours);
						today.put("sleepMinute", minutes);

						BigDecimal sleepGoalHours = profile.getSleepGoalHours();

						// -----------------------------
						// 目標未設定
						// -----------------------------
						if (sleepGoalHours == null || sleepGoalHours.compareTo(BigDecimal.ZERO) <= 0) {
							today.put("sleepGoalStatus", "NO_GOAL");
							today.put("sleepGoalText", "睡眠の目標が設定されていません");

						} else {
							int goalMinutes = sleepGoalHours.multiply(BigDecimal.valueOf(60)).intValue();
							today.put("sleepGoalMinutes", goalMinutes);

							// -----------------------------
							// 目標達成
							// -----------------------------
							if (durationMinutes >= goalMinutes) {
								today.put("sleepGoalStatus", "ACHIEVED");
								today.put("sleepGoalText", "睡眠目標を達成しました");

							}
							// -----------------------------
							// 目標未達成
							// -----------------------------
							else {
								int remainingMinutes = goalMinutes - durationMinutes;
								int remainingHours = remainingMinutes / 60;
								int remainingMins = remainingMinutes % 60;

								today.put("sleepGoalStatus", "NOT_ACHIEVED");
								if (remainingHours > 0) {
									today.put("sleepGoalText", "目標まであと" + remainingHours + "時間" + remainingMins + "分");
								} else {
									today.put("sleepGoalText", "目標まであと" + remainingMins + "分");
								}
							}
						}
					}
				}

			} else {
				today.put("sleepGoalStatus", "NO_RECORD");
				today.put("sleepGoalText", "今日の睡眠は未記録です");
			}
		} catch (BusinessException e) {
			today.put("sleepGoalStatus", "NO_RECORD");
			today.put("sleepGoalText", "今日の睡眠は未記録です");
		}

		// =========================================================
		// Water
		// =========================================================
		try {
			Map<String, Object> result = waterService.list(profileId, currentUserId, todayDate, todayDate, 0);
			Map<String, Object> stats = (Map<String, Object>) result.get("stats");

			if (stats != null) {
				today.put("waterMl", stats.get("todayTotal") != null ? stats.get("todayTotal") : 0);
				today.put("waterPercent", stats.get("goalRate") != null ? stats.get("goalRate") : 0);
			}
		} catch (BusinessException e) {
		}

		// =========================================================
		// Step
		// =========================================================
		try {
			Map<String, Object> result = stepService.list(profileId, currentUserId, todayDate, todayDate, 0);
			Map<String, Object> stats = (Map<String, Object>) result.get("stats");

			if (stats != null) {
				today.put("stepCount", stats.get("todaySteps") != null ? stats.get("todaySteps") : 0);
				today.put("stepPercent", stats.get("goalRate") != null ? stats.get("goalRate") : 0);
			}
		} catch (BusinessException e) {
		}
		return today;
	}

	// ---------------------------------------------------------------------
	// 家族健康サマリー：ProfileService.getProfiles() の実データ + 各プロフィールの
	// 本日分 Weight/Sleep 記録有無をチェックしてステータスを算出する。
	// Water/Step はまだサービスがないため、暫定的に2項目（Weight, Sleep）のみで判定。
	// WaterService/StepService ができたら totalItems を 2 -> 4 に変更し、
	// hasWaterToday / hasStepToday の条件も下に追加すること。
	// ---------------------------------------------------------------------
	private List<Map<String, Object>> buildFamilyMembers(List<Profile> profiles, Long currentUserId, LocalDate today) {
		List<Map<String, Object>> members = new ArrayList<>();

		for (Profile p : profiles) {
			boolean hasWeightToday = hasWeightLogToday(p.getId(), currentUserId, today);
			boolean hasSleepToday = hasSleepLogToday(p.getId(), currentUserId, today);

			int totalItems = 2;
			int doneItems = (hasWeightToday ? 1 : 0) + (hasSleepToday ? 1 : 0);

			String status;
			String statusLabel;
			if (doneItems == totalItems) {
				status = "OK";
				statusLabel = "完了";
			} else if (doneItems == 0) {
				status = "DANGER";
				statusLabel = "未記録";
			} else {
				status = "WARN";
				List<String> missing = new ArrayList<>();
				if (!hasWeightToday)
					missing.add("体重");
				if (!hasSleepToday)
					missing.add("睡眠");
				statusLabel = String.join("・", missing) + "未入力";
			}

			Map<String, Object> member = new HashMap<>();
			member.put("id", p.getId());
			member.put("name", p.getName());
			member.put("age", p.getAge());
			member.put("profileColor", p.getProfileColor());
			member.put("status", status);
			member.put("statusLabel", statusLabel);
			member.put("doneItems", doneItems);
			member.put("totalItems", totalItems);
			member.put("updatedTime", "-");
			members.add(member);
		}
		return members;
	}

	private boolean hasWeightLogToday(Long profileId, Long currentUserId, LocalDate today) {
		try {
			Map<String, Object> result = weightService.list(profileId, currentUserId, today, today, 0);
			List<?> logs = (List<?>) result.get("logs");
			return logs != null && !logs.isEmpty();
		} catch (BusinessException e) {
			return false;
		}
	}

	private boolean hasSleepLogToday(Long profileId, Long currentUserId, LocalDate today) {
		try {
			Map<String, Object> result = sleepService.list(profileId, currentUserId, today, today, 0);
			List<?> logs = (List<?>) result.get("logs");
			return logs != null && !logs.isEmpty();
		} catch (BusinessException e) {
			return false;
		}
	}

	private Map<String, Object> buildFamilySummary(List<Map<String, Object>> familyMembers) {
		long doneCount = familyMembers.stream().filter(m -> "OK".equals(m.get("status"))).count();
		Map<String, Object> summary = new HashMap<>();
		summary.put("doneCount", (int) doneCount);
		summary.put("totalCount", familyMembers.size());
		return summary;
	}
}