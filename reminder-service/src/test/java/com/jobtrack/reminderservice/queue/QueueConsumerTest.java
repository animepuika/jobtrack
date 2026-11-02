package com.jobtrack.reminderservice.queue;

import java.util.ArrayList;
import java.util.List;

import com.jobtrack.reminderservice.queue.EventQueue.QueueItem;
import com.jobtrack.reminderservice.reminder.ReminderEvent;
import com.jobtrack.reminderservice.reminder.ReminderEventHandler;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class QueueConsumerTest {

	private static final String VALID = """
			{"eventId":"5f0b7e64-1f1d-4c8e-9a6e-2d9d3b7f0a11","type":"REMINDER_SCHEDULED",
			 "occurredAt":"2026-11-02T10:00:00Z","ownerId":1,"applicationId":7,
			 "reminderId":"9b1f3c2e-4d5a-4e6f-8a7b-0c1d2e3f4a5b","version":1,
			 "dueAt":"2026-11-09T08:00:00Z","note":"Email the recruiter"}
			""";

	private final FakeQueue queue = new FakeQueue();

	private final ReminderEventHandler handler = mock(ReminderEventHandler.class);

	private final QueueConsumer consumer = new QueueConsumer(this.queue, this.handler, JsonMapper.builder().build(), 3);

	@Test
	void deletesAMessageOnlyAfterItWasHandled() {
		this.queue.items.add(new QueueItem("m1", "p1", VALID, 1));

		this.consumer.pollOnce();

		assertThat(this.queue.deleted).containsExactly("m1");
		assertThat(this.queue.poisoned).isEmpty();
	}

	@Test
	void leavesAFailedMessageOnTheQueueToBeRetried() {
		given(this.handler.handle(any(ReminderEvent.class))).willThrow(new IllegalStateException("database down"));
		this.queue.items.add(new QueueItem("m1", "p1", VALID, 1));

		this.consumer.pollOnce();

		assertThat(this.queue.deleted).isEmpty();
		assertThat(this.queue.poisoned).isEmpty();
	}

	@Test
	void movesAMessageToPoisonAfterTooManyAttempts() {
		given(this.handler.handle(any(ReminderEvent.class))).willThrow(new IllegalStateException("database down"));
		this.queue.items.add(new QueueItem("m1", "p1", VALID, 3));

		this.consumer.pollOnce();

		assertThat(this.queue.poisoned).containsExactly("m1");
	}

	@Test
	void movesUnreadableMessagesToPoisonAtOnce() {
		this.queue.items.add(new QueueItem("m1", "p1", "not json", 1));

		this.consumer.pollOnce();

		assertThat(this.queue.poisoned).containsExactly("m1");
	}

	static class FakeQueue implements EventQueue {

		final List<QueueItem> items = new ArrayList<>();

		final List<String> deleted = new ArrayList<>();

		final List<String> poisoned = new ArrayList<>();

		@Override
		public List<QueueItem> receive(int maxMessages) {
			List<QueueItem> batch = List.copyOf(this.items);
			this.items.clear();
			return batch;
		}

		@Override
		public void delete(QueueItem item) {
			this.deleted.add(item.messageId());
		}

		@Override
		public void moveToPoison(QueueItem item, String reason) {
			this.poisoned.add(item.messageId());
		}

	}

}
