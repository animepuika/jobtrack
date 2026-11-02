package com.jobtrack.jobsource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class JobSourceConfig {

	@Bean
	JobSource jobSource(HiringCafeProperties properties) {
		return properties.enabled() ? new HiringCafeJobSource(properties) : new DisabledJobSource();
	}

}
