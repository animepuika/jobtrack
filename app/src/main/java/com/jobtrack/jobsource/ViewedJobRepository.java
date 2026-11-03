package com.jobtrack.jobsource;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.jobtrack.tracking.SourceProvider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ViewedJobRepository extends JpaRepository<ViewedJob, ViewedJob.Key> {

	/**
	 * Records a view in one statement, so two quick clicks can't both try to insert.
	 */
	@Modifying
	@Query(value = """
			insert into viewed_job (owner_id, source_provider, external_id, first_viewed_at, last_viewed_at)
			values (:ownerId, :provider, :externalId, :now, :now)
			on conflict (owner_id, source_provider, external_id)
			do update set last_viewed_at = excluded.last_viewed_at
			""", nativeQuery = true)
	void markViewed(Long ownerId, String provider, String externalId, Instant now);

	List<ViewedJob> findByOwnerIdAndSourceProviderAndExternalIdIn(Long ownerId, SourceProvider sourceProvider,
			Collection<String> externalIds);

}
