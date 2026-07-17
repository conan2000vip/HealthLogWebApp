package com.healthlog.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable()) // Tắt CSRF để form POST hoạt động
				.authorizeHttpRequests(auth -> auth
						.anyRequest().permitAll() // Cho phép tất cả URL
				)
				.formLogin(login -> login.disable()) // Tắt trang login mặc định
				.logout(logout -> logout.disable()); // Tắt logout mặc định

		return http.build();
	}

	// Mã hóa mật khẩu bằng BCrypt
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
