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

@Controller
public class HomeController {

	// TODO: inject các service thật của bạn, ví dụ:
	// private final WeightService weightService;
	// private final SleepService sleepService;
	// private final WaterService waterService;
	// private final StepService stepService;
	// private final MemoService memoService;
	// private final FeedbackService feedbackService;
	// private final ProfileService profileService;
	// private final FamilyService familyService;
	// private final GoalService goalService;
	//
	// public HomeController(WeightService weightService, SleepService sleepService, ...) {
	// this.weightService = weightService;
	// ...
	// }

	@GetMapping("/profile/{profileId}/home")
	public String home(@PathVariable Long profileId, Model model) {

		LocalDate end = LocalDate.now();
		LocalDate start = end.minusDays(6);

		// ============ Profile + Goal ============
		// TODO: thay bằng profileService.findById(profileId), goalService.findByProfileId(profileId)
		// goals cần có: targetWeight (kg), sleepGoalHour (giờ), waterGoalMl (ml), stepGoal (bước) — 4 mục tiêu ứng 4 màn hình
		model.addAttribute("currentProfile", null); // header fragment tự chịu được null (dùng ?: 'ゲスト')
		model.addAttribute("goals", null);

		// ============ 4 chỉ số hôm nay (weight/sleep/water/step) ============
		Map<String, Object> today = buildToday(profileId);
		model.addAttribute("today", today);

		// ============ Biểu đồ tổng hợp 7 ngày ============
		Map<String, Object> trendChart = buildTrendChart(profileId, start, end);
		model.addAttribute("trendChart", trendChart.isEmpty() ? null : trendChart);

		// ============ Mini sparkline (card 体重 / 睡眠) ============
		model.addAttribute("miniWeightData", getMiniWeightData(profileId));
		model.addAttribute("miniSleepData", getMiniSleepData(profileId));

		// ============ Memo gần nhất ============
		model.addAttribute("recentMemos", getRecentMemos(profileId));

		// ============ Family summary ============
		model.addAttribute("familySummary", getFamilySummary(profileId));
		model.addAttribute("familyMembers", getFamilyMembers(profileId));

		// ============ Feedback (dùng chung fragment layout/data-controls :: feedbackList) ============
		model.addAttribute("feedbackList", getFeedbackList(profileId));

		return "home/home";
	}

	// ---------------------------------------------------------------------
	// Các hàm dưới đây: cái nào bạn ĐÃ có service thì xoá try/catch, gọi thẳng.
	// Cái nào CHƯA có thì cứ để nguyên -> trả rỗng, màn hình tự ẩn phần đó.
	// ---------------------------------------------------------------------

	private Map<String, Object> buildToday(Long profileId) {
		Map<String, Object> today = new HashMap<>();

		// QUAN TRỌNG: Thymeleaf dùng SpringEL (không phải OGNL) -> nếu Map thiếu hẳn key
		// (không phải null, mà là không tồn tại) thì today.xxx trong HTML sẽ ném lỗi
		// "Property or field 'xxx' cannot be found on object of type java.util.HashMap".
		// Nên phải set default null/0 cho ĐỦ key trước, rồi mới ghi đè bằng data thật bên dưới.
		today.put("weightKg", null);
		today.put("bmi", null);
		today.put("sleepHour", null);
		today.put("sleepMinute", null);
		today.put("sleepStatus", null);
		today.put("waterMl", 0);
		today.put("waterPercent", 0);
		today.put("stepCount", 0);
		today.put("stepPercent", 0);

		// TODO: WeightRecord latestWeight = weightService.findLatest(profileId);
		// if (latestWeight != null) {
		// today.put("weightKg", latestWeight.getWeightKg());
		// today.put("bmi", latestWeight.getBmi());
		// }

		// TODO: SleepRecord latestSleep = sleepService.findLatest(profileId);
		// if (latestSleep != null) {
		// today.put("sleepHour", latestSleep.getHour());
		// today.put("sleepMinute", latestSleep.getMinute());
		// today.put("sleepStatus", latestSleep.getStatusLabel());
		// }

		// TODO: int waterMl = waterService.getTodayTotal(profileId);
		// int waterGoal = goalService.getWaterGoal(profileId);
		// today.put("waterMl", waterMl);
		// today.put("waterPercent", waterGoal == 0 ? 0 : Math.min(100, waterMl * 100 / waterGoal));

		// TODO: int stepCount = stepService.getTodayCount(profileId);
		// int stepGoal = goalService.getStepGoal(profileId);
		// today.put("stepCount", stepCount);
		// today.put("stepPercent", stepGoal == 0 ? 0 : Math.min(100, stepCount * 100 / stepGoal));

		return today;
	}

	private Map<String, Object> buildTrendChart(Long profileId, LocalDate start, LocalDate end) {
		List<LocalDate> days = start.datesUntil(end.plusDays(1)).toList();
		List<String> labels = days.stream()
				.map(d -> d.format(DateTimeFormatter.ofPattern("M/d")))
				.toList();

		// TODO: List<WeightRecord> weightRecords = weightService.findRange(profileId, start, end);
		// TODO: List<SleepRecord> sleepRecords = sleepService.findRange(profileId, start, end);
		// TODO: List<WaterRecord> waterRecords = waterService.findRange(profileId, start, end);
		// TODO: List<StepRecord> stepRecords = stepService.findRange(profileId, start, end);

		boolean hasAnyData = false; // set true khi có ít nhất 1 record thật

		if (!hasAnyData) {
			return new HashMap<>(); // chưa có dữ liệu -> chartCard tự ẩn nhờ th:if="${hasData}"
		}

		Map<String, Object> chart = new HashMap<>();
		chart.put("labels", labels);
		chart.put("weight", new ArrayList<Double>()); // TODO: map theo ngày, để null nếu ngày đó thiếu record
		chart.put("sleep", new ArrayList<Double>());
		chart.put("water", new ArrayList<Integer>());
		chart.put("step", new ArrayList<Integer>());
		return chart;
	}

	private List<Double> getMiniWeightData(Long profileId) {
		// TODO: weightService.findLast7Days(profileId) -> map ra List<Double>
		return List.of(); // rỗng -> mini chart không vẽ gì, không lỗi
	}

	private List<Double> getMiniSleepData(Long profileId) {
		// TODO: sleepService.findLast7Days(profileId) -> map ra List<Double>
		return List.of();
	}

	private List<Object> getRecentMemos(Long profileId) {
		// TODO: memoService.findRecent(profileId, 3) -> trả list Memo entity/DTO thật
		// (home.html đang đọc memo.createdAt / memo.content, entity của bạn có field đó thì trả thẳng, khỏi cần DTO)
		return List.of();
	}

	private Map<String, Object> getFamilySummary(Long profileId) {
		// TODO: familyService.getSummary(profileId) -> {doneCount, totalCount}
		return Map.of("doneCount", 0, "totalCount", 0);
	}

	private List<Object> getFamilyMembers(Long profileId) {
		// TODO: familyService.getMembers(profileId)
		return List.of();
	}

	private List<Object> getFeedbackList(Long profileId) {
		// TODO: feedbackService.getFeedback(profileId) -> List<FeedbackItem> (class bạn đã gửi trước đó)
		return List.of(); // rỗng -> fragment feedbackList tự ẩn nhờ th:if="${not #lists.isEmpty(feedbackList)}"
	}
}