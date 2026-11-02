package com.jobtrack.reminderservice.reminder;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ReminderRepository extends JpaRepository<Reminder, UUID> {

	@Query("""
			select r from Reminder r
			where r.ownerId = :ownerId
			  and (:applicationId is null or r.applicationId = :applicationId)
			  and (:status is null or r.status = :status)
			  and r.dueAt is not null
			order by r.dueAt asc, r.id asc
			""")
	List<Reminder> search(Long ownerId, Long applicationId, ReminderStatus status, Pageable page);

}
