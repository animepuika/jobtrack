package com.jobtrack.reminderservice;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "reminders.queue.enabled=false")
class ReminderServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
