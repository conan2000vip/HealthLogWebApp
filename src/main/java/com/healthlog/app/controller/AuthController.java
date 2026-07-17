package com.healthlog.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.healthlog.app.dto.LoginRequest;
import com.healthlog.app.dto.RegisterRequest;
import com.healthlog.app.service.AuthService;

@ControllerAdvice
@Controller
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	// Register
	@GetMapping("/register")
	public String showRegisterForm(Model model) {
		model.addAttribute("registerRequest", new RegisterRequest());
		return "auth/register";
	}

	@PostMapping("/register")
	public String register(
			@ModelAttribute RegisterRequest registerRequest,
			Model model) {

		authService.register(registerRequest);

		model.addAttribute("message",
				"登録が完了しました。メールをご確認ください。");

		return "redirect:/auth/login";
	}

	// Login

	@GetMapping("/login")
	public String showLoginForm(Model model) {
		model.addAttribute("loginRequest", new LoginRequest());
		return "auth/login";
	}

	@PostMapping("/login")
	public String login(
			@ModelAttribute LoginRequest loginRequest,
			HttpServletRequest request,
			HttpServletResponse response) {

		authService.login(loginRequest, request, response);

		return "redirect:/profile/select-profile";
	}

	// Logout

	@PostMapping("/logout")
	public String logout(
			HttpServletRequest request,
			HttpServletResponse response) {

		authService.logout(request, response);

		return "redirect:/auth/login";
	}

	// Email Verification

	@GetMapping("/verify-email")
	public String verifyEmail(
			@RequestParam String token,
			Model model) {

		authService.verifyEmail(token);

		model.addAttribute("message",
				"メール認証が完了しました。");

		return "auth/login";
	}

	// Resend Verification

	@PostMapping("/resend-verification")
	public String resendVerification(
			@RequestParam String email,
			Model model) {

		authService.resendVerification(email);

		model.addAttribute("message",
				"認証メールを送信しました。");

		return "redirect:/auth/login";
	}

	// Forgot Password

	@GetMapping("/forgot-password")
	public String showForgotPasswordPage() {
		return "auth/forgot-password";
	}

	@PostMapping("/forgot-password")
	public String requestPasswordReset(
			@RequestParam String email,
			Model model) {

		authService.requestPasswordReset(email);

		model.addAttribute("message",
				"パスワードリセットメールを送信しました。");

		return "redirect:/auth/forgot-password";
	}

	// Reset Password

	@GetMapping("/password-reset")
	public String showResetPasswordPage(
			@RequestParam String token,
			Model model) {

		model.addAttribute("token", token);

		return "auth/reset-password";
	}

	@PostMapping("/password-reset")
	public String confirmPasswordReset(
			@RequestParam String token,
			@RequestParam String newPassword,
			Model model) {

		authService.confirmPasswordReset(token, newPassword);

		model.addAttribute("message",
				"パスワードを変更しました。");

		return "redirect:/auth/login";
	}

}