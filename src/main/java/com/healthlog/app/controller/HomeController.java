package com.healthlog.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.healthlog.app.service.AuthService;
import com.healthlog.app.service.HomeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {

	private final AuthService authService;
	private final HomeService homeService;

	@GetMapping("/profile/{profileId}/home")
	public String home(@PathVariable Long profileId, Model model) {
		Long currentUserId = authService.getCurrentUser().getId();
		model.addAllAttributes(homeService.getHomeData(profileId, currentUserId));
		return "home/home";
	}
}