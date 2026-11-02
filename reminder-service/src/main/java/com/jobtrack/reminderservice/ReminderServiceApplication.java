package com.jobtrack.reminderservice;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/**
 * Owns reminders for JobTrack. It learns about changes from the tracker's events on
 * a queue and answers the tracker's read requests over an internal API.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ReminderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReminderServiceApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
