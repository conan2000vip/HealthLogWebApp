package com.healthlog.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.healthlog.app.entity.Profile;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {
	List<Profile> findByUser_Id(Long userId);

	Optional<Profile> findByUser_IdAndIsPrimaryTrue(Long userId);

	Optional<Profile> findByIdAndUser_Id(Long profileId, Long userId);

	long countByUser_Id(Long userId);

	boolean existsByUser_Id(Long userId);

	boolean existsByUser_IdAndRelationship(Long userId, String relationship);
}