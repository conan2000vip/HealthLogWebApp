package com.healthlog.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

	@Email(message = "有効なメールアドレスを入力してください")
	@NotBlank(message = "メールアドレスは必須です")
	private String email;

	@NotBlank(message = "パスワードは必須です")
	@Size(min = 8, message = "パスワードは8文字以上で入力してください")
	private String password;

	@NotBlank(message = "アカウント名は必須です")
	@Size(max = 50, message = "アカウント名は50文字以内で入力してください")
	private String accountName;
}
