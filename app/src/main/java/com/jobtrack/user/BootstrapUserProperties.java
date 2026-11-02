package com.jobtrack.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The account to create at startup when it does not exist yet. Set it through the
 * {@code JOBTRACK_USERNAME} and {@code JOBTRACK_PASSWORD} environment variables.
 */
@ConfigurationProperties("jobtrack.bootstrap-user")
public record BootstrapUserProperties(String username, String password) {

	boolean isConfigured() {
		return this.username != null && !this.username.isBlank() && this.password != null
				&& !this.password.isBlank();
	}

}
