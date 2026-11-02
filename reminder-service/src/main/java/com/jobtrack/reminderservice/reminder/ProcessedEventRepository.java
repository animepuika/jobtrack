package com.jobtrack.reminderservice.reminder;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

}
