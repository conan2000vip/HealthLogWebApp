package com.healthlog.app.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "water_logs", indexes = {
		@Index(name = "idx_water_profile_date", columnList = "profile_id, recorded_date")
})
public class Water {

	public enum DrinkType {
		WATER("水"), TEA("お茶"), MILK("牛乳"), COFFEE("コーヒー"), JUICE("ジュース"), SPORTS_DRINK("スポーツドリンク"), OTHER("その他");

		private final String label;

		DrinkType(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_water_logs_profile"))
	private Profile profile;

	@Column(name = "recorded_date", nullable = false)
	private LocalDate recordedDate;

	@Column(name = "recorded_time")
	private LocalTime recordedTime; // nullable, 1日に複数回記録可能

	@Enumerated(EnumType.STRING)
	@Column(name = "drink_type", length = 30, nullable = false)
	private DrinkType drinkType;

	@Column(name = "amount_ml", nullable = false)
	private Integer amountMl;

	@Column(name = "memo")
	private String memo; // nullable

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt = LocalDateTime.now();

	@jakarta.persistence.Transient
	private java.math.BigDecimal goalRate;

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