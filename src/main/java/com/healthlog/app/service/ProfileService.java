package com.healthlog.app.service;

import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Service;

import com.healthlog.app.entity.Profile;
import com.healthlog.app.repository.ProfileRepository;

@Service
public class ProfileService {

	private final ProfileRepository profileRepository;

	public ProfileService(ProfileRepository profileRepository) {
		this.profileRepository = profileRepository;
	}

	public List<Profile> getProfiles(Long userId) {
		return profileRepository.findByUser_Id(userId);
	}

	public Profile getProfile(Long userId, Long profileId) {
		return profileRepository
				.findByIdAndUser_Id(profileId, userId)
				.orElseThrow(() -> new RuntimeException("Profile not found"));
	}

	public Profile create(Profile profile) {
		long count = profileRepository.countByUser_Id(profile.getUser().getId());
		if (count >= 10) {
			throw new IllegalArgumentException("Maximum 10 profiles");
		}
		if (count == 0) {
			profile.setIsPrimary(true);
		}
		return profileRepository.save(profile);
	}

	public Profile update(Profile profile) {
		return profileRepository.save(profile);
	}

	public void delete(Profile profile) {
		profileRepository.delete(profile);
	}

	public void switchProfile(HttpSession session,
			Long userId,
			Long profileId) {

		Profile profile = profileRepository
				.findByIdAndUser_Id(profileId, userId)
				.orElseThrow(() -> new RuntimeException("Profile not found"));
		session.setAttribute("CURRENT_PROFILE_ID", profile.getId());
	}

	public Profile findByIdAndUser(Long profileId, Long userId) {
		return profileRepository
				.findByIdAndUser_Id(profileId, userId)
				.orElseThrow(() -> new RuntimeException("Profile not found"));
	}
}
