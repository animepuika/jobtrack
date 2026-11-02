package com.jobtrack.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

	/**
	 * Locks a batch of due events, oldest first. An event is held back while an
	 * earlier event about the same reminder is still waiting for a retry, so events
	 * about one reminder are always sent in order. {@code skip locked} lets a second
	 * copy of the app take different rows instead of waiting or sending twice.
	 */
	@Query(value = """
			select * from outbox_event e
			where e.published_at is null and e.next_attempt_at <= :now
			  and not exists (
			      select 1 from outbox_event earlier
			      where earlier.aggregate_id = e.aggregate_id
			        and earlier.published_at is null
			        and earlier.next_attempt_at > :now
			        and (earlier.created_at, earlier.id) < (e.created_at, e.id))
			order by e.created_at, e.id
			limit :limit
			for update skip locked
			""", nativeQuery = true)
	List<OutboxEvent> lockDue(Instant now, int limit);

	long countByOwnerIdAndPublishedAtIsNull(Long ownerId);

	long countByPublishedAtIsNull();

}
