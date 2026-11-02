package com.jobtrack.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the configured account on startup. JobTrack has no public sign-up page:
 * it is a personal tool, so accounts come from configuration.
 */
@Component
@Order(1)
class UserBootstrap implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(UserBootstrap.class);

	private final UserAccountService accounts;

	private final BootstrapUserProperties properties;

	UserBootstrap(UserAccountService accounts, BootstrapUserProperties properties) {
		this.accounts = accounts;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.properties.isConfigured()) {
			if (this.accounts.findByUsername(this.properties.username().strip()).isEmpty()) {
				this.accounts.createUser(this.properties.username(), this.properties.password());
				logger.info("Created account '{}'", this.properties.username().strip());
			}
		}
		else if (this.accounts.count() == 0) {
			logger.warn("No accounts exist. Set JOBTRACK_USERNAME and JOBTRACK_PASSWORD to create one.");
		}
	}

}
