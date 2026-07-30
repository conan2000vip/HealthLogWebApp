package com.healthlog.app.controller;

import java.util.Collections;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.User;
import com.healthlog.app.service.AuthService;
import com.healthlog.app.service.ProfileService;

import lombok.RequiredArgsConstructor;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

	private final ProfileService profileService;
	private final AuthService authService;

	@ModelAttribute("currentProfile")
	public Profile currentProfile(
			@PathVariable(required = false) Long profileId,
			HttpSession session) {

		User user = getLoginUserOrNull();
		if (user == null) {
			return null; // 未ログイン（ログイン画面等）
		}

		if (profileId != null) {
			return profileService.getProfile(user.getId(), profileId);
		}

		// URL に profileId が無いページ（/profile/new, /profile/select-profile 等）は
		// セッションの現在プロフィール、または本人プロフィールを使う
		return profileService.resolveCurrentProfile(session, user.getId());
	}

	@ModelAttribute("profileList")
	public List<Profile> profileList() {
		User user = getLoginUserOrNull();
		if (user == null) {
			return Collections.emptyList();
		}
		return profileService.getProfiles(user.getId());
	}

	private User getLoginUserOrNull() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| "anonymousUser".equals(authentication.getPrincipal())) {
			return null;
		}
		try {
			return authService.getCurrentUser();
		} catch (Exception e) {
			return null;
		}
	}
}