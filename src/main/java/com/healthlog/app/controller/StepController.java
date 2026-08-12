package com.healthlog.app.controller;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.healthlog.app.entity.Step;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.service.AuthService;
import com.healthlog.app.service.FeedbackService;
import com.healthlog.app.service.ProfileService;
import com.healthlog.app.service.StepService;
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/profile/{profileId}/step")
@RequiredArgsConstructor
public class StepController {

	private final StepService stepService;
	private final AuthService authService;
	private final ProfileService profileService;
	private final FeedbackService feedbackService;

	@InitBinder
	public void initBinder(WebDataBinder binder) {
		binder.registerCustomEditor(LocalDate.class, new PropertyEditorSupport() {
			@Override
			public void setAsText(String text) {
				if (text == null || text.isBlank()) {
					setValue(null);
				} else {
					setValue(LocalDate.parse(text));
				}
			}
		});
	}

	// ログイン中ユーザーのIDを取得
	private Long currentUserId() {
		return authService.getCurrentUser().getId();
	}

	@GetMapping
	public String list(@PathVariable Long profileId, @RequestParam(required = false) LocalDate startDate,
			@RequestParam(required = false) LocalDate endDate, @RequestParam(defaultValue = "0") int page,
			Model model) {
		Long currentUserId = currentUserId();
		try {
			Map<String, Object> result = stepService.list(profileId, currentUserId, startDate, endDate, page);
			model.addAllAttributes(result);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("logs", java.util.Collections.emptyList());
			model.addAttribute("stats", null);
			model.addAttribute("totalPages", 0);
			model.addAttribute("currentPage", 0);
			model.addAttribute("hasPrevious", false);
			model.addAttribute("hasNext", false);
			model.addAttribute("hasAnyLog", false);
			model.addAttribute("labels", java.util.Collections.emptyList());
			model.addAttribute("values", java.util.Collections.emptyList());
			model.addAttribute("chartMode", "DAY");
			model.addAttribute("chartFrom", startDate);
			model.addAttribute("chartTo", endDate);
		}

		// フィルター条件に関係なく、実データ全体から常に算出する
		List<FeedbackItem> feedbackList = feedbackService.getStepFeedback(profileId);
		model.addAttribute("feedbackList", feedbackList);
		model.addAttribute("profileId", profileId);
		model.addAttribute("currentProfile", profileService.getProfile(currentUserId, profileId));
		model.addAttribute("filterStartDate", startDate);
		model.addAttribute("filterEndDate", endDate);
		return "step/step_logs";
	}

	@org.springframework.web.bind.annotation.ResponseBody
	@GetMapping("/chart-data")
	public Map<String, Object> chartData(@PathVariable Long profileId,
			@RequestParam(required = false) LocalDate startDate, @RequestParam(required = false) LocalDate endDate) {
		return stepService.chartData(profileId, currentUserId(), startDate, endDate);
	}

	// 新規登録・編集: recordId の有無で判定
	@PostMapping("/save")
	public String save(@PathVariable Long profileId, @RequestParam(required = false) Long recordId,
			@RequestParam LocalDate recordedDate, @RequestParam Integer steps,
			@RequestParam(required = false) String memo, RedirectAttributes redirectAttributes) {

		Long currentUserId = currentUserId();

		Step input = new Step();
		input.setRecordedDate(recordedDate);
		input.setSteps(steps);
		input.setMemo(memo);

		try {
			String profileName = profileService.getProfile(currentUserId, profileId).getName();
			if (recordId == null) {
				stepService.create(profileId, currentUserId, input);
				redirectAttributes.addFlashAttribute("message", "「" + profileName + "」の歩数記録を保存しました");
			} else {
				stepService.update(profileId, currentUserId, recordId, input);
				redirectAttributes.addFlashAttribute("message", "「" + profileName + "」の歩数記録を更新しました");
			}
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}

		return "redirect:/profile/" + profileId + "/step";
	}

	@PostMapping("/{logId}/delete")
	public String delete(@PathVariable Long profileId, @PathVariable Long logId,
			RedirectAttributes redirectAttributes) {
		Long currentUserId = currentUserId();
		try {
			String profileName = profileService.getProfile(currentUserId, profileId).getName();
			stepService.delete(profileId, currentUserId, logId);
			redirectAttributes.addFlashAttribute("message", "「" + profileName + "」の歩数記録を削除しました");
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/profile/" + profileId + "/step";
	}
}