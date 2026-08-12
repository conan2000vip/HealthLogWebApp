package com.healthlog.app.service;

import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.healthlog.app.constant.SessionConstants;
import com.healthlog.app.entity.Profile;
import com.healthlog.app.exception.BusinessException;
import com.healthlog.app.repository.ProfileRepository;

@Service
public class ProfileService {

	private static final int MAX_PROFILES = 10;

	// Relationships that can only exist once per user / 1ユーザーにつき1件のみ登録可能な続柄
	private static final List<String> UNIQUE_RELATIONSHIPS = List.of("父", "母", "配偶者");

	private final ProfileRepository profileRepository;

	public ProfileService(ProfileRepository profileRepository) {
		this.profileRepository = profileRepository;
	}

	public List<Profile> getProfiles(Long userId) {
		return profileRepository.findByUser_Id(userId);
	}

	public Profile getProfile(Long userId, Long profileId) {
		return profileRepository.findByIdAndUser_Id(profileId, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "プロファイルが見つかりません"));
	}

	public boolean hasAnyProfile(Long userId) {
		return profileRepository.existsByUser_Id(userId);
	}

	@Transactional
	public Profile create(Profile profile) {
		long count = profileRepository.countByUser_Id(profile.getUser().getId());

		if (count >= MAX_PROFILES) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "プロファイルは最大" + MAX_PROFILES + "件までです");
		}

		if (count == 0) {
			profile.setIsPrimary(true);
			profile.setRelationship("本人");
		} else {
			profile.setIsPrimary(false);

			if (UNIQUE_RELATIONSHIPS.contains(profile.getRelationship())
					&& profileRepository.existsByUser_IdAndRelationship(
							profile.getUser().getId(), profile.getRelationship())) {
				throw new BusinessException(HttpStatus.BAD_REQUEST,
						"「" + profile.getRelationship() + "」はすでに登録されています");
			}
		}

		return profileRepository.save(profile);
	}

	@Transactional
	public Profile update(Profile profile) {
		Profile dbProfile = profileRepository.findById(profile.getId())
				.orElseThrow(() -> new BusinessException(
						HttpStatus.NOT_FOUND,
						"プロファイルが見つかりません"));

		// Keep the primary profile relationship unchanged / 本人プロファイルの続柄は変更しない
		if (Boolean.TRUE.equals(dbProfile.getIsPrimary())) {
			profile.setRelationship(dbProfile.getRelationship());
		} else if (UNIQUE_RELATIONSHIPS.contains(profile.getRelationship())
				&& !profile.getRelationship().equals(dbProfile.getRelationship())
				&& profileRepository.existsByUser_IdAndRelationship(
						dbProfile.getUser().getId(), profile.getRelationship())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST,
					"「" + profile.getRelationship() + "」はすでに登録されています");
		}

		// Update editable profile information / 編集可能なプロフィール情報を更新
		dbProfile.setName(profile.getName());
		dbProfile.setBirthDate(profile.getBirthDate());
		dbProfile.setRelationship(profile.getRelationship());
		dbProfile.setGender(profile.getGender());
		dbProfile.setHeight(profile.getHeight());
		dbProfile.setProfileColor(profile.getProfileColor());

		// Update goals / 目標値を更新
		// NULL means that the goal has not been set / NULLは目標未設定を表す
		dbProfile.setTargetWeight(profile.getTargetWeight());
		dbProfile.setWaterGoalMl(profile.getWaterGoalMl());
		dbProfile.setStepGoal(profile.getStepGoal());
		dbProfile.setSleepGoalHours(profile.getSleepGoalHours());

		return profileRepository.save(dbProfile);
	}

	@Transactional
	public void delete(Long userId, Long profileId, HttpSession session) {
		Profile profile = getProfile(userId, profileId);

		if (Boolean.TRUE.equals(profile.getIsPrimary())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "本人のプロファイルは削除できません");
		}

		profileRepository.delete(profile);

		Long currentId = (Long) session.getAttribute(SessionConstants.CURRENT_PROFILE_ID);

		if (currentId != null && currentId.equals(profileId)) {
			profileRepository.findByUser_IdAndIsPrimaryTrue(userId)
					.ifPresent(primary -> session.setAttribute(
							SessionConstants.CURRENT_PROFILE_ID,
							primary.getId()));
		}
	}

	@Transactional
	public void switchProfile(HttpSession session, Long userId, Long profileId) {
		Profile profile = getProfile(userId, profileId);
		session.setAttribute(SessionConstants.CURRENT_PROFILE_ID, profile.getId());
	}

	public Profile resolveCurrentProfile(HttpSession session, Long userId) {
		Long currentId = (Long) session.getAttribute(SessionConstants.CURRENT_PROFILE_ID);

		if (currentId != null) {
			Optional<Profile> profile = profileRepository.findByIdAndUser_Id(currentId, userId);

			if (profile.isPresent()) {
				return profile.get();
			}
		}

		Optional<Profile> primary = profileRepository.findByUser_IdAndIsPrimaryTrue(userId);

		primary.ifPresent(profile -> session.setAttribute(
				SessionConstants.CURRENT_PROFILE_ID,
				profile.getId()));

		return primary.orElse(null);
	}
}