package com.healthlog.app.config;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.healthlog.app.entity.User;
import com.healthlog.app.service.AuthService;
import com.healthlog.app.service.ProfileService;

@Component
// ユーザーがプロフィールを持っていない場合、プロフィール作成ページにリダイレクトするインターセプター
public class ProfileRequiredInterceptor implements HandlerInterceptor {

	private static final List<String> ALLOWED_PREFIXES = List.of(
			"/profile/new",
			"/profile/manage",
			"/profile/switch",
			"/profile/delete",
			"/profile/select-profile",
			"/auth",
			"/css", "/js", "/image", "/webjars");

	private final ProfileService profileService;
	private final AuthService authService;

	public ProfileRequiredInterceptor(ProfileService profileService, AuthService authService) {
		this.profileService = profileService;
		this.authService = authService;
	}

	private boolean isEditRoute(String uri) {
		return uri.matches("^/profile/\\d+/edit$");
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		String uri = request.getRequestURI();
		if (ALLOWED_PREFIXES.stream().anyMatch(uri::startsWith) || isEditRoute(uri)) {
			return true;
		}
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
			return true;
		}
		User user;
		try {
			user = authService.getCurrentUser();
		} catch (Exception e) {
			return true;
		}
		if (!profileService.hasAnyProfile(user.getId())) {
			response.sendRedirect(request.getContextPath() + "/profile/new");
			return false;
		}
		return true;
	}
}