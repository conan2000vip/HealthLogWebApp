package com.healthlog.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {

	private String token;

	@NotBlank(message = "新しいパスワードを入力してください。")
	@Size(min = 8, max = 64, message = "パスワードは8文字以上で入力してください。")
	private String newPassword;

	@NotBlank(message = "確認用パスワードは必須です")
	private String confirmPassword;

	public String getConfirmPassword() {
		return confirmPassword;
	}

	public void setConfirmPassword(String confirmPassword) {
		this.confirmPassword = confirmPassword;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getNewPassword() {
		return newPassword;
	}

	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}
}