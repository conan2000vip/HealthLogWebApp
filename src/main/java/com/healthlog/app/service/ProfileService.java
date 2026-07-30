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

	private final ProfileRepository profileRepository;

	public ProfileService(ProfileRepository profileRepository) {
		this.profileRepository = profileRepository;
	}

	public List<Profile> getProfiles(Long userId) {
		return profileRepository.findByUser_Id(userId);
	}

	public Profile getProfile(Long userId, Long profileId) {
		return profileRepository.findByIdAndUser_Id(profileId, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "プロフィールが見つかりません"));
	}

	public boolean hasAnyProfile(Long userId) {
		return profileRepository.existsByUser_Id(userId);
	}

	@Transactional
	public Profile create(Profile profile) {
		long count = profileRepository.countByUser_Id(profile.getUser().getId());
		if (count >= MAX_PROFILES) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "プロフィールは最大" + MAX_PROFILES + "件までです");
		}
		profile.setIsPrimary(count == 0);
		return profileRepository.save(profile);
	}

	@Transactional
	public Profile update(Profile profile) {
		return profileRepository.save(profile);
	}

	@Transactional
	public void delete(Long userId, Long profileId, HttpSession session) {
		Profile profile = getProfile(userId, profileId);

		if (Boolean.TRUE.equals(profile.getIsPrimary())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "本人のプロフィールは削除できません");
		}

		profileRepository.delete(profile);

		Long currentId = (Long) session.getAttribute(SessionConstants.CURRENT_PROFILE_ID);
		if (currentId != null && currentId.equals(profileId)) {
			profileRepository.findByUser_IdAndIsPrimaryTrue(userId)
					.ifPresent(primary -> session.setAttribute(SessionConstants.CURRENT_PROFILE_ID, primary.getId()));
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
			if (profile.isPresent())
				return profile.get();
		}

		Optional<Profile> primary = profileRepository.findByUser_IdAndIsPrimaryTrue(userId);
		primary.ifPresent(p -> session.setAttribute(SessionConstants.CURRENT_PROFILE_ID, p.getId()));
		return primary.orElse(null);
	}
}