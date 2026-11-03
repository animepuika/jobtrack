package com.jobtrack.jobsource;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {

	List<SavedSearch> findByOwnerIdOrderByNameAsc(Long ownerId);

	@Query("select s from SavedSearch s where s.ownerId = :ownerId and lower(s.name) = lower(:name)")
	Optional<SavedSearch> findByName(Long ownerId, String name);

	Optional<SavedSearch> findByIdAndOwnerId(Long id, Long ownerId);

	long countByOwnerId(Long ownerId);

}
