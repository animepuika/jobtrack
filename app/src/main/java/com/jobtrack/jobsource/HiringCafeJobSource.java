package com.jobtrack.jobsource;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jobtrack.tracking.SourceProvider;
import com.jobtrack.tracking.SourceUrls;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls HiringCafe's MCP server directly with JSON-RPC over HTTP. No AI model is
 * involved: an MCP tool call is an ordinary request naming the tool and its
 * arguments.
 * <p>
 * Observed behavior (checked on 2026-11-01, see docs/milestone-0-notes.md): no
 * authentication, no session ID, plain JSON responses, structured search results in
 * {@code result._meta["hiringcafe.com/job-cards"]}, and tool errors returned as
 * HTTP 200 with {@code isError: true}.
 */
class HiringCafeJobSource implements JobSource {

	static final String JOB_CARDS = "hiringcafe.com/job-cards";

	private static final String PROTOCOL_VERSION = "2025-06-18";

	private static final int MAX_ATTEMPTS = 2;

	private static final Pattern JOB_ID_LINE = Pattern.compile("\\*\\*Job ID:\\*\\*\\s*(\\S+)");

	private final HiringCafeProperties properties;

	private final RestClient restClient;

	private final AtomicLong requestIds = new AtomicLong();

	HiringCafeJobSource(HiringCafeProperties properties) {
		this.properties = properties;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	@Override
	public SourceProvider provider() {
		return SourceProvider.HIRINGCAFE;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public JobSearchPage search(JobSearchQuery query) {
		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.put("search", query.keywords());
		if (query.location() != null) {
			arguments.put("location", query.location());
		}
		if (query.workplace() != null) {
			arguments.put("workplace_types", List.of(query.workplace().label()));
		}
		if (query.seniority() != null) {
			arguments.put("seniority_levels", List.of(query.seniority().label()));
		}
		if (!query.excludedCompanies().isEmpty()) {
			arguments.put("exclude_company_names", query.excludedCompanies());
		}
		arguments.put("limit", this.properties.maxResults());
		ToolResult result = callTool("search_jobs", arguments);
		JobCards cards = (result.meta() != null) ? result.meta().get(JOB_CARDS) : null;
		if (cards == null || cards.jobs() == null) {
			throw new JobSourceException("HiringCafe answered in an unexpected format");
		}
		List<JobSearchResult> results = new ArrayList<>();
		Map<String, String> summaries = summariesById(result.firstText());
		for (JobCard card : cards.jobs()) {
			toResult(card, summaries.get(card.id())).ifPresent(results::add);
		}
		int total = (cards.total() != null) ? cards.total() : results.size();
		return new JobSearchPage(List.copyOf(results), total);
	}

	private ToolResult callTool(String tool, Map<String, Object> arguments) {
		Map<String, Object> request = Map.of("jsonrpc", "2.0", "id", this.requestIds.incrementAndGet(), "method",
				"tools/call", "params", Map.of("name", tool, "arguments", arguments));
		RpcResponse response = send(request);
		if (response == null) {
			throw new JobSourceException("HiringCafe sent an empty response");
		}
		if (response.error() != null) {
			throw new JobSourceException("HiringCafe reported an error: " + response.error().message());
		}
		ToolResult result = response.result();
		if (result == null) {
			throw new JobSourceException("HiringCafe answered in an unexpected format");
		}
		if (Boolean.TRUE.equals(result.isError())) {
			throw new JobSourceException("HiringCafe could not run the search: " + result.firstText());
		}
		return result;
	}

	private RpcResponse send(Map<String, Object> request) {
		for (int attempt = 1;; attempt++) {
			try {
				return this.restClient.post()
					.uri(this.properties.endpoint())
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
					.header("MCP-Protocol-Version", PROTOCOL_VERSION)
					.body(request)
					.retrieve()
					.onStatus((status) -> status.value() == 429, (req, res) -> {
						throw new JobSourceException("HiringCafe is limiting requests. Try again later.",
								retryAfter(res.getHeaders()), false, null);
					})
					.onStatus(HttpStatusCode::isError, (req, res) -> {
						throw new JobSourceException("HiringCafe answered with HTTP " + res.getStatusCode().value(),
								retryAfter(res.getHeaders()), res.getStatusCode().is5xxServerError(), null);
					})
					.body(RpcResponse.class);
			}
			catch (JobSourceException ex) {
				if (!ex.isRetryable() || attempt >= MAX_ATTEMPTS) {
					throw ex;
				}
			}
			catch (ResourceAccessException ex) {
				if (attempt >= MAX_ATTEMPTS) {
					String message = (ex.getCause() instanceof HttpTimeoutException)
							? "HiringCafe did not respond in time"
							: "HiringCafe could not be reached. Check the internet connection, or whether a network filter blocks hiringcafe.com.";
					throw new JobSourceException(message, null, true, ex);
				}
			}
			catch (RestClientException ex) {
				throw new JobSourceException("HiringCafe answered in an unexpected format", null, false, ex);
			}
		}
	}

	private static Optional<JobSearchResult> toResult(JobCard card, String summary) {
		if (isBlank(card.id()) || isBlank(card.title()) || isBlank(card.company())) {
			return Optional.empty();
		}
		List<String> highlights = (card.meta() != null) ? card.meta().stream().filter(Objects::nonNull).toList()
				: List.of();
		String url;
		try {
			url = SourceUrls.normalize(card.url());
		}
		catch (IllegalArgumentException ex) {
			url = null;
		}
		return Optional.of(new JobSearchResult(card.id(), card.title(), card.company(),
				locationFrom(highlights), highlights, url, summary));
	}

	/**
	 * The markdown part of a search response has one section per job: a heading,
	 * labelled lines ending with "**Job ID:** …", then a one-paragraph requirements
	 * summary. Returns that summary by job ID. Display text can change, so a missing
	 * summary is simply left out.
	 */
	static Map<String, String> summariesById(String markdown) {
		Map<String, String> summaries = new HashMap<>();
		if (markdown == null) {
			return summaries;
		}
		for (String section : markdown.split("\n### ")) {
			Matcher id = JOB_ID_LINE.matcher(section);
			if (!id.find()) {
				continue;
			}
			String rest = section.substring(id.end());
			int end = rest.indexOf("\n---");
			String summary = ((end >= 0) ? rest.substring(0, end) : rest).lines()
				.map(String::strip)
				.filter((line) -> !line.isEmpty() && !line.startsWith("-") && !line.startsWith("**"))
				.collect(Collectors.joining(" "));
			if (!summary.isEmpty()) {
				summaries.put(id.group(1), summary);
			}
		}
		return summaries;
	}

	/**
	 * The first highlight looks like {@code "Riga, Riga · Hybrid"}: the place, then the
	 * workplace type. This is display text, not a structured field, so it is only a
	 * best guess.
	 */
	static String locationFrom(List<String> highlights) {
		if (highlights.isEmpty()) {
			return null;
		}
		String first = highlights.getFirst();
		int separator = first.indexOf(" · ");
		String location = (separator >= 0) ? first.substring(0, separator) : first;
		return location.isBlank() ? null : location.strip();
	}

	static Duration retryAfter(HttpHeaders headers) {
		String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
		if (value == null) {
			return null;
		}
		try {
			return Duration.ofSeconds(Long.parseLong(value.strip()));
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RpcResponse(ToolResult result, RpcError error) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RpcError(int code, String message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ToolResult(List<Content> content, Boolean isError, @JsonProperty("_meta") Map<String, JobCards> meta) {

		String firstText() {
			if (this.content == null) {
				return "no details given";
			}
			return this.content.stream()
				.map(Content::text)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse("no details given");
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Content(String type, String text) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record JobCards(List<JobCard> jobs, Integer total) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record JobCard(String id, String title, String company, List<String> meta, String url) {
	}

}
