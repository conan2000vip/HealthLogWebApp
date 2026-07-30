package com.healthlog.app.controller;

import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.healthlog.app.constant.SessionConstants;
import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.User;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.repository.UserRepository;
import com.healthlog.app.service.ProfileService;

@Controller
@RequestMapping("/profile")
public class ProfileController {

	private static final List<String> PROFILE_COLORS = List.of(
			"#4a7fe0", "#2ec4a0", "#f5a623", "#e85d75", "#9b6dd6",
			"#5cc9f5", "#f2994a", "#6fcf97", "#eb5757", "#bb6bd9");

	private final ProfileService profileService;
	private final UserRepository userRepository;

	public ProfileController(ProfileService profileService, UserRepository userRepository) {
		this.profileService = profileService;
		this.userRepository = userRepository;
	}

	@GetMapping("/{id}/select")
	public String selectProfile(
			@PathVariable Long id,
			HttpSession session) {
		User user = getLoginUser();
		profileService.switchProfile(session, user.getId(), id);
		return "redirect:/profile/" + id + "/home";
	}

	@GetMapping("/select-profile")
	public String selectProfile(Model model) {
		User user = getLoginUser();
		model.addAttribute("profiles", profileService.getProfiles(user.getId()));
		return "profile/select-profile";
	}

	@GetMapping("/profile-manage")
	public String manageProfile(Model model) {
		User user = getLoginUser();
		model.addAttribute("profiles", profileService.getProfiles(user.getId()));
		return "profile/profile-manage";
	}

	@PostMapping("/switch/{id}")
	public String switchProfile(@PathVariable Long id, HttpSession session,
			@RequestHeader(value = "Referer", required = false) String referer,
			RedirectAttributes redirectAttributes) {

		User user = getLoginUser();
		try {
			profileService.switchProfile(session, user.getId(), id);
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:" + (referer != null ? referer : "/home");
	}

	// ===== Create =====

	@GetMapping("/new")
	public String newProfileForm(Model model) {
		User user = getLoginUser();
		model.addAttribute("profile", new Profile());
		model.addAttribute("isFirstProfile", !profileService.hasAnyProfile(user.getId()));
		model.addAttribute("profileColors", PROFILE_COLORS);
		return "profile/profile-form";
	}

	@PostMapping("/new")
	public String createProfile(@ModelAttribute() Profile profile,
			BindingResult bindingResult, Model model, HttpSession session) {
		User user = getLoginUser();
		boolean isFirstProfile = !profileService.hasAnyProfile(user.getId());
		validate(profile, bindingResult, isFirstProfile);
		if (bindingResult.hasErrors()) {
			model.addAttribute("isFirstProfile", isFirstProfile);
			model.addAttribute("profileColors", PROFILE_COLORS);
			return "profile/profile-form";
		}
		profile.setId(null);
		profile.setUser(user);

		// 最初のプロフィールは続柄を強制的に「本人」にする（クライアント入力を信用しない）
		// isPrimary は ProfileService.create() 内で自動判定される
		if (isFirstProfile) {
			profile.setRelationship("本人");
		}
		try {
			profile = profileService.create(profile);
		} catch (BusinessException e) {
			model.addAttribute("errorMessage", e.getMessage());
			model.addAttribute("isFirstProfile", isFirstProfile);
			model.addAttribute("profileColors", PROFILE_COLORS);
			return "profile/profile-form";
		}
		session.setAttribute(SessionConstants.CURRENT_PROFILE_ID, profile.getId());
		return "redirect:/profile/" + profile.getId() + "/home";
	}

	// ===== Update =====

	@GetMapping("/{id}/edit")
	public String editProfileForm(@PathVariable Long id, Model model) {
		User user = getLoginUser();
		try {
			model.addAttribute("profile", profileService.getProfile(user.getId(), id));
		} catch (BusinessException e) {
			model.addAttribute("errorMessage", e.getMessage());
			model.addAttribute("profiles", profileService.getProfiles(user.getId()));
			return "profile/profile-manage";
		}
		model.addAttribute("isFirstProfile", false);
		model.addAttribute("profileColors", PROFILE_COLORS);
		return "profile/profile-form";
	}

	@PostMapping("/{id}/edit")
	public String updateProfile(@PathVariable Long id,
			@ModelAttribute("profile") Profile formProfile,
			BindingResult bindingResult, Model model) {
		User user = getLoginUser();
		validate(formProfile, bindingResult, false);
		if (bindingResult.hasErrors()) {
			model.addAttribute("isFirstProfile", false);
			model.addAttribute("profileColors", PROFILE_COLORS);
			return "profile/profile-form";
		}
		try {
			Profile profile = profileService.getProfile(user.getId(), id);
			profile.setName(formProfile.getName());
			profile.setBirthDate(formProfile.getBirthDate());
			profile.setRelationship(formProfile.getRelationship());
			profile.setGender(formProfile.getGender());
			profile.setHeight(formProfile.getHeight());
			profile.setTargetWeight(formProfile.getTargetWeight());
			profile.setWaterGoalMl(formProfile.getWaterGoalMl());
			profile.setStepGoal(formProfile.getStepGoal());
			profile.setSleepGoalHours(formProfile.getSleepGoalHours());
			profile.setProfileColor(formProfile.getProfileColor());
			profileService.update(profile);
		} catch (BusinessException e) {
			model.addAttribute("errorMessage", e.getMessage());
			model.addAttribute("isFirstProfile", false);
			model.addAttribute("profileColors", PROFILE_COLORS);
			return "profile/profile-form";
		}
		return "redirect:/profile/profile-manage";
	}

	// ===== Delete =====

	@PostMapping("/delete")
	public String deleteProfile(@RequestParam Long profileId, HttpSession session,
			RedirectAttributes redirectAttributes) {
		User user = getLoginUser();
		try {
			profileService.delete(user.getId(), profileId, session);
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/profile/profile-manage";
	}

	// ===== helpers =====

	private void validate(Profile profile, BindingResult bindingResult, boolean isFirstProfile) {
		if (profile.getName() == null || profile.getName().isBlank()) {
			bindingResult.rejectValue("name", "required", "名前を入力してください");
		}
		if (!isFirstProfile && (profile.getRelationship() == null || profile.getRelationship().isBlank())) {
			bindingResult.rejectValue("relationship", "required", "続柄を選択してください");
		}
		if (profile.getWaterGoalMl() == null) {
			bindingResult.rejectValue("waterGoalMl", "required", "水分目標を入力してください");
		}
		if (profile.getStepGoal() == null) {
			bindingResult.rejectValue("stepGoal", "required", "歩数目標を入力してください");
		}
		if (profile.getSleepGoalHours() == null) {
			bindingResult.rejectValue("sleepGoalHours", "required", "睡眠目標を入力してください");
		}
	}

	private User getLoginUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String email = authentication.getName();
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));
	}
}