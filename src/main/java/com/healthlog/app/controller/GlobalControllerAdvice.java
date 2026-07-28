package com.healthlog.app.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

import com.healthlog.app.entity.Profile;
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
			@PathVariable(required = false) Long profileId) {

		if (profileId == null) {
			return null;
		}

		Long userId = authService.getCurrentUser().getId();

		return profileService.getProfile(userId, profileId);
	}
}