package com.healthlog.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.healthlog.app.dto.ForgotPasswordRequest;
import com.healthlog.app.dto.LoginRequest;
import com.healthlog.app.dto.RegisterRequest;
import com.healthlog.app.dto.ResetPasswordRequest;
import com.healthlog.app.service.AuthService;

@Controller
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	// AuthServiceが投げるResponseStatusExceptionは、getMessage()を使うと
	// "400 BAD_REQUEST \"...\"" のようにステータスコードまで含まれてしまうため、
	// getReason()から純粋なメッセージ部分だけを取り出す
	private String extractErrorMessage(Exception e) {
		if (e instanceof ResponseStatusException rse && rse.getReason() != null) {
			return rse.getReason();
		}
		return e.getMessage();
	}

	// Register

	@GetMapping("/register")
	public String showRegisterForm(Model model) {
		model.addAttribute("registerRequest", new RegisterRequest());
		return "auth/register";
	}

	@PostMapping("/register")
	public String register(
			@Valid @ModelAttribute("registerRequest") RegisterRequest registerRequest,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return "auth/register";
		}
		try {
			authService.register(registerRequest);
			redirectAttributes.addFlashAttribute(
					"message",
					"登録が完了しました。メールをご確認ください。");
			return "redirect:/auth/login";
		} catch (Exception e) {
			// リダイレクトすると入力内容が失われるため、登録画面に留まる
			model.addAttribute("error", extractErrorMessage(e));
			return "auth/register";
		}
	}

	// Login

	@GetMapping("/login")
	public String showLoginForm(Model model) {
		model.addAttribute("loginRequest", new LoginRequest());
		return "auth/login";
	}

	@PostMapping("/login")
	public String login(
			@Valid @ModelAttribute("loginRequest") LoginRequest loginRequest,
			BindingResult bindingResult,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model) {

		if (bindingResult.hasErrors()) {
			return "auth/login";
		}
		try {
			authService.login(loginRequest, request, response);
			return "redirect:/profile/select-profile";
		} catch (Exception e) {
			// リダイレクトしないため Model で問題ない
			model.addAttribute("error", extractErrorMessage(e));
			return "auth/login";
		}
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
			@RequestParam(required = false) String token,
			RedirectAttributes redirectAttributes) {
		try {
			authService.verifyEmail(token);
			redirectAttributes.addFlashAttribute(
					"message",
					"メール認証が完了しました。");
			return "redirect:/auth/login";
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute(
					"error",
					extractErrorMessage(e));
			return "redirect:/auth/login";
		}
	}

	// Resend Verification

	@PostMapping("/resend-verification")
	public String resendVerification(
			@RequestParam String email,
			RedirectAttributes redirectAttributes) {
		try {
			authService.resendVerification(email);
			redirectAttributes.addFlashAttribute(
					"message",
					"認証メールを送信しました。");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute(
					"error",
					extractErrorMessage(e));
		}
		return "redirect:/auth/login";
	}

	// Forgot Password

	@GetMapping("/forgot-password")
	public String showForgotPasswordPage(Model model) {
		model.addAttribute(
				"forgotPasswordRequest",
				new ForgotPasswordRequest());
		return "auth/forgot-password";
	}

	@PostMapping("/forgot-password")
	public String requestPasswordReset(
			@Valid @ModelAttribute("forgotPasswordRequest") ForgotPasswordRequest request,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		// バリデーションエラーがある場合は、元のページに戻す
		if (bindingResult.hasErrors()) {
			return "auth/forgot-password";
		}
		try {
			authService.requestPasswordReset(
					request.getEmail());
			redirectAttributes.addFlashAttribute(
					"message",
					"パスワードリセットメールを送信しました。");
			return "redirect:/auth/forgot-password";
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute(
					"error",
					extractErrorMessage(e));
			return "redirect:/auth/forgot-password";
		}
	}

	// Reset Password

	@GetMapping("/password-reset")
	public String showResetPasswordPage(
			@RequestParam String token,
			Model model) {
		ResetPasswordRequest request = new ResetPasswordRequest();
		request.setToken(token);
		model.addAttribute(
				"resetPasswordRequest",
				request);
		return "auth/reset-password";
	}

	@PostMapping("/password-reset")
	public String confirmPasswordReset(
			@Valid @ModelAttribute("resetPasswordRequest") ResetPasswordRequest request,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return "auth/reset-password";
		}
		if (!request.getNewPassword().equals(request.getConfirmPassword())) {
			bindingResult.rejectValue(
					"confirmPassword",
					"password.mismatch",
					"パスワードが一致しません。");
			return "auth/reset-password";
		}
		try {
			authService.confirmPasswordReset(
					request.getToken(),
					request.getNewPassword());
			redirectAttributes.addFlashAttribute(
					"message",
					"パスワードを変更しました。");
			return "redirect:/auth/login";
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute(
					"error",
					extractErrorMessage(e));
			return "redirect:/auth/login";
		}
	}
}