package com.jobtrack.reminderservice.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class InternalApiConfig {

	@Bean
	FilterRegistrationBean<InternalTokenFilter> internalTokenFilter(
			@Value("${reminders.internal-api.token}") String token) {
		FilterRegistrationBean<InternalTokenFilter> registration = new FilterRegistrationBean<>(
				new InternalTokenFilter(token));
		registration.addUrlPatterns("/internal/*");
		return registration;
	}

}
