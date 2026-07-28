package com.healthlog.app.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "auth_tokens", indexes = {
		@Index(name = "idx_auth_tokens_user_type", columnList = "user_id, token_type"),
		@Index(name = "idx_auth_tokens_token", columnList = "token", unique = true),
		@Index(name = "idx_auth_tokens_expire", columnList = "expires_at")
})
public class AuthToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_auth_tokens_user"))
	private User user;

	@Column(name = "token", length = 512, nullable = false, unique = true)
	private String token;

	@Column(name = "token_type", length = 30, nullable = false)
	private String tokenType; // "email_verification" | "password_reset"

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "used_flg", nullable = false)
	private Boolean usedFlg = false; // 0=未使用, 1=使用済

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
	}
}
