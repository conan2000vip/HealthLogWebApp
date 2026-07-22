package com.healthlog.app.controller;

import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.healthlog.app.entity.Weight;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.service.AuthService;
import com.healthlog.app.service.WeightService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/profile/{profileId}/weight")
@RequiredArgsConstructor
public class WeightController {

	private final WeightService weightService;
	private final AuthService authService;

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

	// ログイン中ユーザーのIDを取得（profileId がこのユーザーのものか WeightService 側で検証する）
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
		Map<String, Object> result = weightService.list(profileId, currentUserId, startDate, endDate, page);
		model.addAllAttributes(result);
		model.addAttribute("profileId", profileId);
		model.addAttribute("filterStartDate", startDate);
		model.addAttribute("filterEndDate", endDate);
		return "weight/weight_logs";
	}

	// 新規登録・編集: フォームは共通、recordId の有無で判定
	@PostMapping("/save")
	public String save(
			@PathVariable Long profileId,
			@RequestParam(required = false) Long recordId,
			@RequestParam LocalDate date,
			@RequestParam BigDecimal weight,
			@RequestParam(required = false) BigDecimal height,
			@RequestParam(required = false) String memo,
			RedirectAttributes redirectAttributes) {
		Long currentUserId = currentUserId();
		Weight input = new Weight();
		input.setRecordedDate(date);
		input.setWeight(weight);
		input.setHeight(height);
		input.setMemo(memo);
		try {
			if (recordId == null) {
				// 新規登録
				weightService.create(profileId, currentUserId, input);
				redirectAttributes.addFlashAttribute("message", "体重を記録しました");
			} else {
				// 編集
				weightService.update(profileId, currentUserId, recordId, input);
				redirectAttributes.addFlashAttribute("message", "体重を更新しました");
			}
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/profile/" + profileId + "/weight";
	}

	@PostMapping("/{logId}/delete")
	public String delete(
			@PathVariable Long profileId,
			@PathVariable Long logId,
			RedirectAttributes redirectAttributes) {
		Long currentUserId = currentUserId();
		try {
			weightService.delete(profileId, currentUserId, logId);
			redirectAttributes.addFlashAttribute("message", "削除しました");
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/profile/" + profileId + "/weight";
	}
}