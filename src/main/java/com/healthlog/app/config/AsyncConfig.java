package com.healthlog.app.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableAsync
// このアノテーションを付与することで、非同期処理を有効化します。
public class AsyncConfig implements WebMvcConfigurer {

	private final ProfileRequiredInterceptor profileRequiredInterceptor;

	public AsyncConfig(ProfileRequiredInterceptor profileRequiredInterceptor) {
		this.profileRequiredInterceptor = profileRequiredInterceptor;
	}

	@Bean(name = "mailExecutor")
	Executor mailExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(5);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("mail-");
		executor.initialize();
		return executor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(profileRequiredInterceptor)
				.addPathPatterns("/**");
	}
}