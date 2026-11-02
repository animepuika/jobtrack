package com.jobtrack.reminders;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReminderRepository extends JpaRepository<Reminder, Long> {

	Optional<Reminder> findByIdAndOwnerId(Long id, Long ownerId);

	List<Reminder> findByOwnerIdAndApplicationIdOrderByDueAtAscIdAsc(Long ownerId, Long applicationId);

	List<Reminder> findByOwnerIdAndCompletedAtIsNullOrderByDueAtAscIdAsc(Long ownerId, Pageable page);

	List<Reminder> findByOwnerIdOrderByDueAtAscIdAsc(Long ownerId);

}
