package com.jobtrack.reminderservice.api;

import java.time.Instant;
import java.util.UUID;

import com.jobtrack.reminderservice.TestcontainersConfiguration;
import com.jobtrack.reminderservice.reminder.ReminderEvent;
import com.jobtrack.reminderservice.reminder.ReminderEvent.Type;
import com.jobtrack.reminderservice.reminder.ReminderEventHandler;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "reminders.queue.enabled=false", "reminders.internal-api.token=test-token-1234567890" })
@AutoConfigureMockMvc
class InternalReminderApiTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ReminderEventHandler handler;

	@Test
	void rejectsRequestsWithoutTheToken() throws Exception {
		this.mvc.perform(get("/internal/reminders").param("ownerId", "1")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/internal/reminders").param("ownerId", "1").header("Authorization", "Bearer wrong"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void listsOneOwnersRemindersSoonestFirst() throws Exception {
		long owner = System.nanoTime();
		schedule(owner, Instant.parse("2026-11-20T08:00:00Z"), "Later");
		schedule(owner, Instant.parse("2026-11-10T08:00:00Z"), "Sooner");
		schedule(owner + 1, Instant.parse("2026-11-05T08:00:00Z"), "Someone else's");

		this.mvc
			.perform(get("/internal/reminders").param("ownerId", String.valueOf(owner))
				.param("status", "OPEN")
				.header("Authorization", "Bearer test-token-1234567890"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].note").value("Sooner"))
			.andExpect(jsonPath("$[1].note").value("Later"));
	}

	private void schedule(long owner, Instant dueAt, String note) {
		this.handler.handle(new ReminderEvent(UUID.randomUUID(), Type.REMINDER_SCHEDULED, Instant.now(), owner, 3,
				UUID.randomUUID(), 1, dueAt, note));
	}

}
