package com.jobtrack.jobsource;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("jobtrack.hiringcafe")
public record HiringCafeProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("https://hiringcafe.com/api/mcp") URI endpoint, @DefaultValue("3s") Duration connectTimeout,
		@DefaultValue("10s") Duration readTimeout, @DefaultValue("10") int maxResults) {

}
