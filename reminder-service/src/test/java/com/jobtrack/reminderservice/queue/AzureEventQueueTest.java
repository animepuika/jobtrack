package com.jobtrack.reminderservice.queue;

import java.time.Duration;
import java.util.List;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.jobtrack.reminderservice.queue.EventQueue.QueueItem;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Talks to a real Azurite queue in Docker, so the Azure SDK calls are checked, not
 * just the logic around them.
 */
@Testcontainers(disabledWithoutDocker = true)
class AzureEventQueueTest {

	private static final String ACCOUNT_KEY = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

	@Container
	static final GenericContainer<?> azurite = new GenericContainer<>("mcr.microsoft.com/azure-storage/azurite:3.37.0")
		.withCommand("azurite-queue", "--queueHost", "0.0.0.0")
		.withExposedPorts(10001);

	@Test
	void receivesDeletesAndPoisonsMessages() {
		String connection = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=" + ACCOUNT_KEY
				+ ";QueueEndpoint=http://" + azurite.getHost() + ":" + azurite.getMappedPort(10001)
				+ "/devstoreaccount1;";
		QueueProperties properties = new QueueProperties(true, connection, "events-test", "events-test-poison",
				Duration.ofSeconds(30), 5);
		AzureEventQueue queue = new AzureEventQueue(properties);
		QueueClient sender = new QueueClientBuilder().connectionString(connection).queueName("events-test").buildClient();
		sender.sendMessage("{\"hello\":1}");
		sender.sendMessage("{\"hello\":2}");

		List<QueueItem> received = queue.receive(10);
		assertThat(received).extracting(QueueItem::body).containsExactlyInAnyOrder("{\"hello\":1}", "{\"hello\":2}");
		assertThat(received).allSatisfy((item) -> assertThat(item.dequeueCount()).isEqualTo(1));

		queue.delete(received.get(0));
		queue.moveToPoison(received.get(1), "test");

		assertThat(queue.receive(10)).isEmpty();
		QueueClient poison = new QueueClientBuilder().connectionString(connection)
			.queueName("events-test-poison")
			.buildClient();
		assertThat(poison.receiveMessages(10)).extracting((message) -> message.getBody().toString())
			.containsExactly(received.get(1).body());
	}

}
