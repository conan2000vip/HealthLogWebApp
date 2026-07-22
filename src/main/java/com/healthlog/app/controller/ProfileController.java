package com.healthlog.app.controller;

import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.User;
import com.healthlog.app.repository.ProfileRepository;
import com.healthlog.app.repository.UserRepository;

@Controller
@RequestMapping("/profiles")
public class ProfileController {

	private final ProfileRepository profileRepository;
	private final UserRepository userRepository;

	public ProfileController(
			ProfileRepository profileRepository,
			UserRepository userRepository) {
		this.profileRepository = profileRepository;
		this.userRepository = userRepository;
	}

	// Login後のプロフィール選択画面

	@GetMapping("/select")
	public String selectProfile(Model model) {
		User user = getLoginUser();
		List<Profile> profiles = profileRepository.findByUser_Id(user.getId());
		model.addAttribute("profiles", profiles);
		return "profile/select-profile";
	}

	// プロフィール管理画面

	@GetMapping("/manage")
	public String manageProfile(Model model) {
		User user = getLoginUser();
		List<Profile> profiles = profileRepository.findByUser_Id(user.getId());
		model.addAttribute("profiles", profiles);
		return "profile/profile-manage";
	}

	// プロフィール切替

	@PostMapping("/select/{id}")
	public String switchProfile(
			@PathVariable Long id,
			HttpSession session) {
		User user = getLoginUser();
		Profile profile = profileRepository
				.findByIdAndUser_Id(id, user.getId())
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND,
						"Profile not found"));
		session.setAttribute(
				"selectedProfileId",
				profile.getId());
		return "redirect:/home";
	}

	// ログインユーザー取得

	private User getLoginUser() {
		Authentication authentication = SecurityContextHolder
				.getContext()
				.getAuthentication();
		System.out.println(
				"Authentication = " + authentication);
		System.out.println(
				"Username = " + authentication.getName());
		String email = authentication.getName();
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.UNAUTHORIZED,
						"User not found"));
	}
}