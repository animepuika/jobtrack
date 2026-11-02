package com.jobtrack.reminderservice;

import org.springframework.boot.SpringApplication;

public class TestReminderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ReminderServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
