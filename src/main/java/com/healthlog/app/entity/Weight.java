package com.healthlog.app.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "weight_logs", indexes = {
		@Index(name = "idx_weight_profile_date", columnList = "profile_id, recorded_date")
})
public class Weight {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_weight_logs_profile"))
	private Profile profile;

	@Column(name = "recorded_date", nullable = false)
	private LocalDate recordedDate;

	@Column(name = "measured_at", nullable = false)
	private LocalDateTime measuredAt;

	@Column(name = "weight", precision = 5, scale = 1, nullable = false)
	private BigDecimal weight; // kg

	@Column(name = "height", precision = 5, scale = 1)
	private BigDecimal height; // nullable, cm。記録時点の身長。BMI計算・成長記録用

	@Column(name = "memo")
	private String memo; // nullable

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt = LocalDateTime.now();

	@Transient
	private BigDecimal bmi;

	@Transient
	private String bmiStatus;

	@Transient
	private String bmiStatusCode;

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}