package com.jobtrack.reminderservice.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Only the tracker may read reminders. It sends a shared secret as a bearer token;
 * the comparison takes the same time whatever the input, so the token cannot be
 * guessed from response times.
 */
class InternalTokenFilter extends OncePerRequestFilter {

	private final byte[] expected;

	InternalTokenFilter(String token) {
		if (token == null || token.length() < 16) {
			throw new IllegalStateException("reminders.internal-api.token must be at least 16 characters");
		}
		this.expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/internal/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		byte[] given = (header != null) ? header.getBytes(StandardCharsets.UTF_8) : new byte[0];
		if (!MessageDigest.isEqual(this.expected, given)) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		chain.doFilter(request, response);
	}

}
