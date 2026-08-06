package com.healthlog.app.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "profiles", indexes = {
		@Index(name = "idx_profiles_user_id", columnList = "user_id"),
		@Index(name = "idx_profiles_user_primary", columnList = "user_id, is_primary")
})
public class Profile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_profiles_user"))
	private User user;

	@Column(name = "name", length = 100, nullable = false)
	private String name;

	@Column(name = "birth_date")
	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate birthDate;

	@Column(name = "relationship", length = 20, nullable = false)
	private String relationship;

	@Column(name = "gender", length = 10)
	private String gender; // nullable

	@Column(name = "height", precision = 5, scale = 1)
	private BigDecimal height; // cm, nullable

	@Column(name = "target_weight", precision = 5, scale = 1)
	private BigDecimal targetWeight; // nullable

	@Column(name = "water_goal_ml", nullable = false)
	private Integer waterGoalMl = 1500;

	@Column(name = "step_goal", nullable = false)
	private Integer stepGoal = 8000;

	@Column(name = "daily_sleep_goal", precision = 3, scale = 1, nullable = false)
	private BigDecimal sleepGoalHours = new BigDecimal("8.0");

	@Column(name = "water_goal_set", nullable = false)
	private Boolean waterGoalSet = false;

	@Column(name = "sleep_goal_set", nullable = false)
	private Boolean sleepGoalSet = false;

	@Column(name = "step_goal_set", nullable = false)
	private Boolean stepGoalSet = false;

	@Column(name = "profile_color", length = 20)
	private String profileColor; // nullable

	@Column(name = "is_primary", nullable = false)
	private Boolean isPrimary = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt = LocalDateTime.now();

	// relationships with logs
	@OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Weight> weightLogs;

	@OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Sleep> sleepLogs;

	@OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Water> waterLogs;

	@OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Step> stepLogs;

	@OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Memo> memoLogs;

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

	//Age form datebirth
	@Transient
	public Integer getAge() {
		if (birthDate == null) {
			return null;
		}
		return Period.between(birthDate, LocalDate.now()).getYears();
	}
}