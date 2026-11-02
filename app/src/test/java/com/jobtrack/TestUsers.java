package com.jobtrack;

import java.util.UUID;

import com.jobtrack.user.AppUser;
import com.jobtrack.user.JobTrackUser;
import com.jobtrack.user.UserAccountService;

import org.springframework.stereotype.Component;

/**
 * Creates a fresh user for each test. Tests share one database, but each only sees
 * its own user's records, which is exactly the ownership rule being tested.
 */
@Component
public class TestUsers {

	public static final String PASSWORD = "test-password";

	private final UserAccountService accounts;

	public TestUsers(UserAccountService accounts) {
		this.accounts = accounts;
	}

	public JobTrackUser create() {
		AppUser user = this.accounts.createUser("user-" + UUID.randomUUID(), PASSWORD);
		return new JobTrackUser(user.getId(), user.getUsername(), user.getPasswordHash());
	}

}
