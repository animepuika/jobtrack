package com.jobtrack.jobsource;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.jobtrack.tracking.SourceProvider;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remembers which search results the user has opened, so results can say "Opened
 * today" the next time they come up.
 */
@Service
@Transactional
public class ViewedJobService {

	private final ViewedJobRepository viewed;

	private final Clock clock;

	ViewedJobService(ViewedJobRepository viewed, Clock clock) {
		this.viewed = viewed;
		this.clock = clock;
	}

	public void markViewed(long ownerId, SourceProvider provider, String externalId) {
		this.viewed.markViewed(ownerId, provider.name(), externalId, Instant.now(this.clock));
	}

	/**
	 * When each of these jobs was last opened, for the ones that were.
	 */
	@Transactional(readOnly = true)
	public Map<String, Instant> lastViewed(long ownerId, SourceProvider provider, Collection<String> externalIds) {
		if (externalIds.isEmpty()) {
			return Map.of();
		}
		return this.viewed.findByOwnerIdAndSourceProviderAndExternalIdIn(ownerId, provider, externalIds)
			.stream()
			.collect(Collectors.toMap(ViewedJob::getExternalId, ViewedJob::getLastViewedAt));
	}

}
