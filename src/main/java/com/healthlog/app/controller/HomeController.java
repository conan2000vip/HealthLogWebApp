package com.healthlog.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.healthlog.app.entity.User;
import com.healthlog.app.service.AuthService;

@Controller
public class HomeController {

	private final AuthService authService;

	public HomeController(AuthService authService) {
		this.authService = authService;
	}

	@GetMapping("/profile/{profileId}/home")
	public String home(Model model) {
		User user = authService.getCurrentUser();
		model.addAttribute("user", user);
		return "home";
	}
}