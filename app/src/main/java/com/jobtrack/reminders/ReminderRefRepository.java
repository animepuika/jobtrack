package com.jobtrack.reminders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReminderRefRepository extends JpaRepository<ReminderRef, UUID> {

	Optional<ReminderRef> findByIdAndOwnerId(UUID id, Long ownerId);

	List<ReminderRef> findByOwnerIdAndApplicationIdAndOpenTrue(Long ownerId, Long applicationId);

}
