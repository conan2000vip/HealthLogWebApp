package com.healthlog.app.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.service.AuthService;
import com.healthlog.app.service.FeedbackService;
import com.healthlog.app.service.ProfileService;
import com.healthlog.app.service.SleepService;
import com.healthlog.app.service.WeightService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {

	private final AuthService authService;
	private final ProfileService profileService;
	private final WeightService weightService;
	private final SleepService sleepService;
	private final FeedbackService feedbackService;

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
		Map<String, Object> today = buildToday(profileId);
		model.addAttribute("today", today);

		// ============ 7日間の推移グラフ ============
		Map<String, Object> trendChart = buildTrendChart(profileId, start, end);
		model.addAttribute("trendChart", trendChart.isEmpty() ? null : trendChart);

		// ============ 健康メモ ============
		model.addAttribute("recentMemos", getRecentMemos(profileId));

		// ============ 家族健康サマリー：上で取得した profileList から現在表示中のプロフィールを除外 ============
		List<Profile> otherProfiles = profileList.stream()
				.filter(p -> !p.getId().equals(currentProfile.getId()))
				.toList();
		List<Map<String, Object>> familyMembers = buildFamilyMembers(otherProfiles, currentUserId, end);
		model.addAttribute("familyMembers", familyMembers);
		model.addAttribute("familySummary", buildFamilySummary(familyMembers));

		// ============ フィードバック（fragment layout/data-controls :: feedbackList） ============
		model.addAttribute("feedbackList", feedbackService.getHomeFeedback(profileId));

		return "home/home";
	}

	private Map<String, Object> buildToday(Long profileId) {
		Map<String, Object> today = new HashMap<>();
		today.put("weightKg", null);
		today.put("bmi", null);
		today.put("sleepHour", null);
		today.put("sleepMinute", null);
		today.put("sleepStatus", null);
		today.put("waterMl", 0);
		today.put("waterPercent", 0);
		today.put("stepCount", 0);
		today.put("stepPercent", 0);

		// TODO: buildFamilyMembers() と同じパターンで weightService.list(...) / sleepService.list(...) を連携する
		// TODO: waterService / stepService ができ次第、対応する

		return today;
	}

	private Map<String, Object> buildTrendChart(Long profileId, LocalDate start, LocalDate end) {
		List<LocalDate> days = start.datesUntil(end.plusDays(1)).toList();
		List<String> labels = days.stream()
				.map(d -> d.format(DateTimeFormatter.ofPattern("M/d")))
				.toList();

		boolean hasAnyData = false; // TODO: weight/sleep/water/step の7日間データを実装したら true にする

		if (!hasAnyData) {
			return new HashMap<>(); // データがまだない -> chartCard は th:if="${hasData}" により自動的に非表示になる
		}

		Map<String, Object> chart = new HashMap<>();
		chart.put("labels", labels);
		chart.put("weight", new ArrayList<Double>());
		chart.put("sleep", new ArrayList<Double>());
		chart.put("water", new ArrayList<Integer>());
		chart.put("step", new ArrayList<Integer>());
		return chart;
	}

	private List<Object> getRecentMemos(Long profileId) {
		// TODO: memoService.findRecent(profileId, 3)
		return List.of();
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

			int totalItems = 2; // TODO: Water/Step 追加時に 4 へ変更
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
			member.put("updatedTime", "-"); // TODO: 実際の記録時刻（measuredAt/createdAt）を表示したい場合はここを実装
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