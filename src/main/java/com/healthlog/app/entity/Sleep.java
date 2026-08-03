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
@Table(name = "sleep_logs", indexes = {
		@Index(name = "idx_sleep_profile_date", columnList = "profile_id, recorded_date"),
})
public class Sleep {

	public enum SleepType {
		NIGHT, // 夜間睡眠
		NAP // 昼寝
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "profile_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sleep_logs_profile"))
	private Profile profile;

	@Column(name = "recorded_date", nullable = false)
	private LocalDate recordedDate; // 起床した日を記録日とする

	@Column(name = "start_time")
	private LocalTime startTime; // nullable, 就寝開始時刻

	@Column(name = "end_time")
	private LocalTime endTime; // nullable, 起床時刻

	@Enumerated(EnumType.STRING)
	@Column(name = "sleep_type", nullable = false)
	private SleepType sleepType = SleepType.NIGHT;

	@Column(name = "sleep_minutes")
	private Integer sleepMinutes; // nullable, 分単位で自動計算

	@Column(name = "memo")
	private String memo; // nullable

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt = LocalDateTime.now();

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