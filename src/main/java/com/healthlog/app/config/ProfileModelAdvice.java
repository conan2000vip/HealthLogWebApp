package com.healthlog.app.config;

import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.entity.User;
import com.healthlog.app.repository.UserRepository;
import com.healthlog.app.service.ProfileService;

@ControllerAdvice
public class ProfileModelAdvice {

	private final ProfileService profileService;
	private final UserRepository userRepository;

	public ProfileModelAdvice(ProfileService profileService, UserRepository userRepository) {
		this.profileService = profileService;
		this.userRepository = userRepository;
	}

	@ModelAttribute
	public void addProfileAttributes(Model model, HttpSession session) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
			return; // chưa login (trang login/register) -> bỏ qua
		}

		User user = userRepository.findByEmail(auth.getName()).orElse(null);
		if (user == null)
			return;

		List<Profile> profiles = profileService.getProfiles(user.getId());
		Profile currentProfile = profileService.resolveCurrentProfile(session, user.getId());

		model.addAttribute("profileList", profiles);
		model.addAttribute("currentProfile", currentProfile);
	}
}