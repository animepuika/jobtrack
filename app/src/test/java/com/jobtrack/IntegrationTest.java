package com.jobtrack;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * A test that starts the whole application against a real PostgreSQL. Live
 * HiringCafe calls are off, so tests never depend on the internet. The queue and the
 * reminder service are replaced by an in-memory stand-in, and the outbox publisher
 * does not run on its own: tests call it when they want events delivered.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = { "jobtrack.hiringcafe.enabled=false", "jobtrack.documents.directory=target/test-documents",
		"jobtrack.events.publisher.enabled=false" })
@Import({ TestcontainersConfiguration.class, TestRemindersConfiguration.class })
public @interface IntegrationTest {

}
