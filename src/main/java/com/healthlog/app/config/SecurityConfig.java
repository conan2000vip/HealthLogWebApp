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
	// SecurityFilterChainを定義することで、Spring Securityの設定をカスタマイズできます。
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(
								(request, response, authException) -> response.sendRedirect("/auth/login")))
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/auth/**",
								"/css/**",
								"/js/**",
								"/image/**",
								"/favicon.ico")
						.permitAll()
						.anyRequest().authenticated())
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessUrl("/auth/login?logout=true"));
		return http.build();
	}

	// PasswordEncoder beanを定義することで、Spring Securityがパスワードのハッシュ化にBCryptを使用するようになります。
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
