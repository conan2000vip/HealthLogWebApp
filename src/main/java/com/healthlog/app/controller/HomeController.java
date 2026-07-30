package com.healthlog.app.controller;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.healthlog.app.constant.SessionConstants;
import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.User;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.repository.UserRepository;
import com.healthlog.app.service.ProfileService;

@Controller
@RequestMapping("/profile")
public class HomeController {

	private final ProfileService profileService;
	private final UserRepository userRepository;

	public HomeController(ProfileService profileService, UserRepository userRepository) {
		this.profileService = profileService;
		this.userRepository = userRepository;
	}

	@GetMapping("/{id}/home")
	public String home(@PathVariable Long id, HttpSession session, Model model) {
		User user = getLoginUser();
		Profile profile = profileService.getProfile(user.getId(), id);
		session.setAttribute(SessionConstants.CURRENT_PROFILE_ID, profile.getId());
		return "home/home";
	}

	private User getLoginUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String email = authentication.getName();
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "ユーザーが見つかりません"));
	}
}