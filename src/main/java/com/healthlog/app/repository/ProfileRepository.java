package com.healthlog.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Profile;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {

	// idx_profiles_user_id — ユーザーごとのプロフィール一覧取得
	List<Profile> findByUser_Id(Long userId);

	// idx_profiles_user_primary — 本人プロフィールの高速検索
	Optional<Profile> findByUser_IdAndIsPrimaryTrue(Long userId);

	// swith profile
	Optional<Profile> findByIdAndUser_Id(Long profileId, Long userId);

	long countByUser_Id(Long userId);

}