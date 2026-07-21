package com.healthlog.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordRequest {

	@NotBlank(message = "メールアドレスを入力してください。")
	@Email(message = "メールアドレスの形式が正しくありません。")
	private String email;

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

}
