package com.healthlog.app.controller;

import java.beans.PropertyEditorSupport;
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

import com.healthlog.app.entity.Memo;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.service.AuthService;
import com.healthlog.app.service.MemoService;
import com.healthlog.app.service.ProfileService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/profile/{profileId}/memo")
@RequiredArgsConstructor
public class MemoController {

	private final MemoService memoService;
	private final AuthService authService;
	private final ProfileService profileService;

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

	// Current user / ログイン中ユーザー
	private Long currentUserId() {
		return authService.getCurrentUser().getId();
	}

	// ---------------------------------------------------------
	// Memo list / メモ一覧
	// ---------------------------------------------------------
	@GetMapping
	public String list(@PathVariable Long profileId, @RequestParam(required = false) LocalDate startDate,
			@RequestParam(required = false) LocalDate endDate, @RequestParam(defaultValue = "0") int page, Model model) {

		Long currentUserId = currentUserId();

		try {
			Map<String, Object> result = memoService.list(profileId, currentUserId, startDate, endDate, page);
			model.addAllAttributes(result);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("logs", java.util.Collections.emptyList());
			model.addAttribute("totalPages", 0);
			model.addAttribute("currentPage", 0);
			model.addAttribute("hasPrevious", false);
			model.addAttribute("hasNext", false);
			model.addAttribute("hasAnyLog", false);
		}

		model.addAttribute("profileId", profileId);
		model.addAttribute("currentProfile", profileService.getProfile(currentUserId, profileId));
		model.addAttribute("filterStartDate", startDate);
		model.addAttribute("filterEndDate", endDate);

		return "memo/memo_logs";
	}

	// ---------------------------------------------------------
	// Create / Update / 新規登録・更新
	// ---------------------------------------------------------
	@PostMapping("/save")
	public String save(@PathVariable Long profileId, @RequestParam(required = false) Long recordId,
			@RequestParam LocalDate recordedDate, @RequestParam(required = false) String title,
			@RequestParam String content, RedirectAttributes redirectAttributes) {

		Long currentUserId = currentUserId();

		Memo input = new Memo();
		input.setRecordedDate(recordedDate);
		input.setTitle(title);
		input.setContent(content);

		try {
			String profileName = profileService.getProfile(currentUserId, profileId).getName();

			if (recordId == null) {
				memoService.create(profileId, currentUserId, input);
				redirectAttributes.addFlashAttribute("message", "「" + profileName + "」のメモを保存しました");
			} else {
				memoService.update(profileId, currentUserId, recordId, input);
				redirectAttributes.addFlashAttribute("message", "「" + profileName + "」のメモを更新しました");
			}
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}

		return "redirect:/profile/" + profileId + "/memo";
	}

	// ---------------------------------------------------------
	// Delete / 削除
	// ---------------------------------------------------------
	@PostMapping("/{logId}/delete")
	public String delete(@PathVariable Long profileId, @PathVariable Long logId, RedirectAttributes redirectAttributes) {
		Long currentUserId = currentUserId();

		try {
			String profileName = profileService.getProfile(currentUserId, profileId).getName();
			memoService.delete(profileId, currentUserId, logId);
			redirectAttributes.addFlashAttribute("message", "「" + profileName + "」のメモを削除しました");
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}

		return "redirect:/profile/" + profileId + "/memo";
	}
}