package com.healthlog.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailService.class);

	private final JavaMailSender mailSender;
	private final String from;
	private final String frontendUrl;

	public EmailService(
			JavaMailSender mailSender,
			@Value("${app.mail.from}") String from,
			@Value("${app.frontend-url}") String frontendUrl) {
		this.mailSender = mailSender;
		this.from = from;
		this.frontendUrl = frontendUrl;
	}

	// メール認証メール送信
	@Async("mailExecutor")
	public void sendVerificationEmail(String email, String token) {
		// AuthController の @RequestMapping("/auth") + @GetMapping("/verify-email") と必ず一致させること
		String verifyUrl = frontendUrl
				+ "/auth/verify-email?token="
				+ token;
		log.info("Verification URL: {}", verifyUrl);

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("HealthLog メール認証");
		message.setText(
				"HealthLogへご登録ありがとうございます。\n\n"
						+ "以下のリンクをクリックしてメール認証を完了してください。\n\n"
						+ verifyUrl
						+ "\n\n"
						+ "このリンクの有効期限は24時間です。\n");
		// メール送信
		try {
			mailSender.send(message);
			log.info("Verification email sent to {}", maskEmail(email));
		} catch (MailException e) {
			log.error("Failed to send verification email to {}: {}", maskEmail(email), e.getMessage());
		}
	}

	// パスワードリセットメール送信
	@Async("mailExecutor")
	public void sendPasswordResetEmail(String email, String token) {
		// AuthController の @RequestMapping("/auth") + @GetMapping("/password-reset") と必ず一致させること
		String resetUrl = frontendUrl
				+ "/auth/password-reset?token="
				+ token;
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("HealthLog パスワードリセット");
		message.setText(
				"パスワードリセットの要求を受け付けました。\n\n"
						+ "以下のリンクから新しいパスワードを設定してください。\n\n"
						+ resetUrl
						+ "\n\n"
						+ "このリンクの有効期限は30分です。\n\n"
						+ "心当たりがない場合は、このメールを無視してください。\n");
		try {
			mailSender.send(message);
			log.info("Password reset email sent to {}", maskEmail(email));
		} catch (MailException e) {
			log.error("Failed to send password reset email to {}: {}", maskEmail(email), e.getMessage());
		}
	}

	// メールアドレスのマスキング（ログ出力用）
	private String maskEmail(String email) {
		int at = email.indexOf('@');
		if (at <= 1) {
			return "***" + (at >= 0 ? email.substring(at) : "");
		}
		return email.charAt(0) + "***" + email.substring(at);
	}
}