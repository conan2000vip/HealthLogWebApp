package com.healthlog.app.controller;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.LocalTime;
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

import com.healthlog.app.entity.Water;
import com.healthlog.app.entity.Water.DrinkType;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.service.AuthService;
import com.healthlog.app.service.FeedbackService;
import com.healthlog.app.service.ProfileService;
import com.healthlog.app.service.WaterService;
import com.healthlog.app.service.feedbackservice.model.FeedbackItem;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/profile/{profileId}/water")
@RequiredArgsConstructor
public class WaterController {

	private final WaterService waterService;
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

	private Long currentUserId() {
		return authService.getCurrentUser().getId();
	}

	@GetMapping
	public String list(
			@PathVariable Long profileId,
			@RequestParam(required = false) LocalDate startDate,
			@RequestParam(required = false) LocalDate endDate,
			@RequestParam(defaultValue = "0") int page,
			Model model) {
		Long currentUserId = currentUserId();
		try {
			Map<String, Object> result = waterService.list(profileId, currentUserId, startDate, endDate, page);
			model.addAllAttributes(result);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("logs", java.util.Collections.emptyList());
			model.addAttribute("stats", null);
			model.addAttribute("hasAnyLog", false);
			model.addAttribute("totalPages", 0);
			model.addAttribute("currentPage", 0);
			model.addAttribute("hasPrevious", false);
			model.addAttribute("hasNext", false);
			model.addAttribute("labels", java.util.Collections.emptyList());
			model.addAttribute("values", java.util.Collections.emptyList());
		}

		// フィルター条件に関係なく、実データ全体から常に算出する
		List<FeedbackItem> feedbackList = feedbackService.getWaterFeedback(profileId);
		model.addAttribute("feedbackList", feedbackList);

		model.addAttribute("profileId", profileId);
		model.addAttribute("filterStartDate", startDate);
		model.addAttribute("filterEndDate", endDate);
		return "water/water_logs";
	}

	@PostMapping("/save")
	public String save(
			@PathVariable Long profileId,
			@RequestParam(required = false) Long recordId,
			@RequestParam LocalDate recordedDate,
			@RequestParam(required = false) LocalTime recordedTime,
			@RequestParam DrinkType drinkType,
			@RequestParam Integer amount,
			@RequestParam(required = false) String memo,
			RedirectAttributes redirectAttributes) {

		Long currentUserId = currentUserId();

		Water input = new Water();
		input.setRecordedDate(recordedDate);
		input.setRecordedTime(recordedTime);
		input.setDrinkType(drinkType);
		input.setAmountMl(amount);
		input.setMemo(memo);
		try {
			String profileName = profileService.getProfile(currentUserId, profileId).getName();
			if (recordId == null) {
				waterService.create(profileId, currentUserId, input);
				redirectAttributes.addFlashAttribute("message",
						"「" + profileName + "」の水分記録を保存しました");
			} else {
				waterService.update(profileId, currentUserId, recordId, input);
				redirectAttributes.addFlashAttribute("message",
						"「" + profileName + "」の水分記録を更新しました");
			}
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/profile/" + profileId + "/water";
	}

	@PostMapping("/{logId}/delete")
	public String delete(
			@PathVariable Long profileId,
			@PathVariable Long logId,
			RedirectAttributes redirectAttributes) {
		Long currentUserId = currentUserId();
		try {
			String profileName = profileService.getProfile(currentUserId, profileId).getName();
			waterService.delete(profileId, currentUserId, logId);
			redirectAttributes.addFlashAttribute("message",
					"「" + profileName + "」の水分記録を削除しました");
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/profile/" + profileId + "/water";
	}
}