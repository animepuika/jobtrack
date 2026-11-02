package com.jobtrack.tracking;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface TrackedJobRepository extends JpaRepository<TrackedJob, Long> {

	Optional<TrackedJob> findByOwnerIdAndSourceProviderAndExternalId(Long ownerId, SourceProvider provider,
			String externalId);

	Optional<TrackedJob> findByOwnerIdAndSourceProviderAndSourceUrl(Long ownerId, SourceProvider provider,
			String sourceUrl);

}
