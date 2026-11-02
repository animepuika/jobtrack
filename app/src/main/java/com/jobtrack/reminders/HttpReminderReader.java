package com.jobtrack.reminders;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Calls the reminder service's internal API with the shared token. Short timeouts
 * keep pages fast when the service is down.
 */
@Component
class HttpReminderReader implements ReminderReader {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	private static final int ALL = 500;

	private final RestClient client;

	private final URI baseUrl;

	HttpReminderReader(@Value("${jobtrack.reminders.service-url}") URI baseUrl,
			@Value("${jobtrack.reminders.token}") String token) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
		requestFactory.setReadTimeout(TIMEOUT);
		this.baseUrl = baseUrl;
		this.client = RestClient.builder()
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.build();
	}

	@Override
	public List<ReminderView> forApplication(long ownerId, long applicationId) {
		return fetch(ownerId, applicationId, null, ALL);
	}

	@Override
	public List<ReminderView> upcoming(long ownerId, int limit) {
		return fetch(ownerId, null, "OPEN", limit);
	}

	@Override
	public List<ReminderView> all(long ownerId) {
		return fetch(ownerId, null, null, ALL);
	}

	private List<ReminderView> fetch(long ownerId, Long applicationId, String status, int limit) {
		URI uri = UriComponentsBuilder.fromUri(this.baseUrl)
			.path("/internal/reminders")
			.queryParam("ownerId", ownerId)
			.queryParamIfPresent("applicationId", Optional.ofNullable(applicationId))
			.queryParamIfPresent("status", Optional.ofNullable(status))
			.queryParam("limit", limit)
			.build()
			.toUri();
		try {
			ReminderView[] reminders = this.client.get().uri(uri).retrieve().body(ReminderView[].class);
			return (reminders != null) ? Arrays.asList(reminders) : List.of();
		}
		catch (RestClientException ex) {
			throw new RemindersUnavailableException(ex);
		}
	}

}
