package com.jobtrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two sets of rules. The JSON API under {@code /api} uses HTTP Basic on every
 * request and keeps no session, so browser cookies cannot be used to call it and
 * CSRF tokens are not needed. The web pages use a login form, a session cookie and
 * CSRF tokens on every form.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	@Order(1)
	SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
		http.securityMatcher("/api/**")
			.authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
			.httpBasic(Customizer.withDefaults())
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf((csrf) -> csrf.disable());
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((requests) -> requests.requestMatchers("/login", "/css/**", "/js/**", "/error")
			.permitAll()
			.anyRequest()
			.authenticated())
			.formLogin((form) -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
			.logout((logout) -> logout.logoutSuccessUrl("/login?logout"));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
