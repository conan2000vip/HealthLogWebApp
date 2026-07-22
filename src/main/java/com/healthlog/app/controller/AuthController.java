package com.healthlog.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.service.AuthService;

@Controller
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	// ===== 登録 =====

	@GetMapping("/register")
	public String registerForm() {
		return "auth/register";
	}

	@PostMapping("/register")
	public String register(
			@RequestParam String accountName,
			@RequestParam String email,
			@RequestParam String password,
			RedirectAttributes redirectAttributes) {
		try {
			authService.register(accountName, email, password);
			redirectAttributes.addFlashAttribute(
					"message",
					"登録が完了しました。確認メールをご確認のうえ、メール認証を行ってください。");
			return "redirect:/auth/login";
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			// 入力値を保持してユーザーの再入力負担を軽減（パスワードは保持しない）
			redirectAttributes.addFlashAttribute("accountName", accountName);
			redirectAttributes.addFlashAttribute("email", email);
			return "redirect:/auth/register";
		}
	}

	// ===== ログイン / ログアウト =====

	@GetMapping("/login")
	public String loginForm() {
		return "auth/login";
	}

	@PostMapping("/login")
	public String login(
			@RequestParam String email,
			@RequestParam String password,
			HttpServletRequest request,
			HttpServletResponse response,
			RedirectAttributes redirectAttributes) {
		try {
			authService.login(email, password, request, response);
			return "redirect:/";
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			redirectAttributes.addFlashAttribute("email", email);
			// login.html はこのフラグを見て「確認メールを再送する」ボタンの表示を切り替える
			if (e.getStatus() == HttpStatus.FORBIDDEN) {
				redirectAttributes.addFlashAttribute("emailNotVerified", true);
			}
			return "redirect:/auth/login";
		}
	}

	@PostMapping("/logout")
	public String logout(HttpServletRequest request, HttpServletResponse response) {
		authService.logout(request, response);
		return "redirect:/auth/login";
	}

	// ===== メール認証 =====

	@GetMapping("/verify-email")
	public String verifyEmail(
			@RequestParam String token,
			RedirectAttributes redirectAttributes) {
		try {
			authService.verifyEmail(token);
			redirectAttributes.addFlashAttribute(
					"message",
					"メール認証が完了しました。ログインしてください。");
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/auth/login";
	}

	// 専用ページは持たず、login.html 上の「確認メールを再送する」ボタンから呼び出す
	@PostMapping("/resend-verification")
	public String resendVerification(
			@RequestParam String email,
			RedirectAttributes redirectAttributes) {
		try {
			authService.resendVerification(email);
		} catch (BusinessException e) {
			// 入力形式エラー（空欄・不正な形式）のみユーザーに表示
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			return "redirect:/auth/login";
		}
		// メール存在有無・認証済み有無に関わらず同一メッセージを表示（列挙攻撃対策）
		redirectAttributes.addFlashAttribute(
				"message",
				"確認メールを送信しました。届いていない場合は迷惑メールフォルダもご確認ください。");
		return "redirect:/auth/login";
	}

	// ===== パスワードリセット =====

	@GetMapping("/forgot-password")
	public String forgotPasswordForm() {
		return "auth/forgot-password";
	}

	@PostMapping("/forgot-password")
	public String requestPasswordReset(
			@RequestParam String email,
			RedirectAttributes redirectAttributes) {
		try {
			authService.requestPasswordReset(email);
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			redirectAttributes.addFlashAttribute("email", email);
			return "redirect:/auth/forgot-password";
		}
		// メール存在有無に関わらず同一メッセージを表示（列挙攻撃対策）
		redirectAttributes.addFlashAttribute(
				"message",
				"パスワード再設定用のメールを送信しました。");
		return "redirect:/auth/login";
	}

	@GetMapping("/password-reset")
	public String resetPasswordForm(
			@RequestParam String token,
			Model model) {
		model.addAttribute("token", token);
		return "auth/reset-password";
	}

	@PostMapping("/password-reset")
	public String confirmPasswordReset(
			@RequestParam String token,
			@RequestParam String newPassword,
			@RequestParam String confirmPassword,
			RedirectAttributes redirectAttributes) {
		// 画面側の入力確認チェック（newPassword と confirmPassword の一致）
		if (!newPassword.equals(confirmPassword)) {
			redirectAttributes.addFlashAttribute("error", "パスワードが一致しません");
			redirectAttributes.addFlashAttribute("token", token);
			return "redirect:/auth/password-reset?token=" + token;
		}

		try {
			authService.confirmPasswordReset(token, newPassword);
			redirectAttributes.addFlashAttribute(
					"message",
					"パスワードを変更しました。新しいパスワードでログインしてください。");
			return "redirect:/auth/login";
		} catch (BusinessException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			redirectAttributes.addFlashAttribute("token", token);
			return "redirect:/auth/password-reset?token=" + token;
		}
	}
}