package com.jobtrack.matching;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UserSkillRepository extends JpaRepository<UserSkill, UserSkill.Key> {

	List<UserSkill> findByOwnerIdOrderByWeightDescNameAsc(Long ownerId);

	@Modifying
	@Query("delete from UserSkill s where s.ownerId = :ownerId and s.source = 'CV'")
	void deleteCvSkills(Long ownerId);

	@Modifying
	@Query("delete from UserSkill s where s.ownerId = :ownerId and lower(s.name) = lower(:name)")
	int deleteByName(Long ownerId, String name);

}
