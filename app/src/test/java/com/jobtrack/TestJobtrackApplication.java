package com.jobtrack;

import org.springframework.boot.SpringApplication;

/**
 * Runs JobTrack locally with a Testcontainers PostgreSQL and the fictional demo data:
 * {@code ./mvnw spring-boot:test-run}.
 */
public class TestJobtrackApplication {

	public static void main(String[] args) {
		SpringApplication.from(JobtrackApplication::main)
			.with(TestcontainersConfiguration.class)
			.withAdditionalProfiles("demo")
			.run(args);
	}

}
