package com.jobtrack.jobsource;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

	private static final double KM_PER_MILE = 1.609344;

	private static final Pattern JOB_ID_LINE = Pattern.compile("\\*\\*Job ID:\\*\\*\\s*(\\S+)");

	private static final Pattern POSTED_LINE = Pattern.compile("\\*\\*Posted:\\*\\*\\s*(\\S+)");

	private static final Pattern FACT_LINE = Pattern.compile("-\\s+\\*\\*([^*]+?):\\*\\*\\s*(.*)");

	private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");

	private static final Pattern JOB_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");

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
		ToolResult result = callTool("search_jobs", arguments(query, this.properties.maxResults()));
		JobCards cards = (result.meta() != null) ? result.meta().get(JOB_CARDS) : null;
		if (cards == null || cards.jobs() == null) {
			throw new JobSourceException("HiringCafe answered in an unexpected format");
		}
		List<JobSearchResult> results = new ArrayList<>();
		Map<String, String> summaries = summariesById(result.firstText());
		Map<String, LocalDate> postedDates = postedDatesById(result.firstText());
		for (JobCard card : cards.jobs()) {
			toResult(card, summaries.get(card.id()), postedDates.get(card.id())).ifPresent(results::add);
		}
		int total = (cards.total() != null) ? cards.total() : results.size();
		return new JobSearchPage(List.copyOf(results), total);
	}

	@Override
	public JobDetails details(String externalId) {
		if (externalId == null || !JOB_ID.matcher(externalId).matches()) {
			throw new JobSourceException("That isn't a HiringCafe job ID");
		}
		ToolResult result = callTool("get_job_details", Map.of("job_id", externalId));
		return parseDetails(externalId, result.firstText());
	}

	/**
	 * Reads the {@code get_job_details} markdown: a linked title, the company in bold,
	 * labelled lines such as "- **Compensation:** …", then "## Requirements summary" and
	 * "## Full description" sections. Display text can change, so every part is
	 * optional except the title.
	 */
	static JobDetails parseDetails(String externalId, String markdown) {
		String title = null;
		String url = null;
		String company = null;
		Map<String, String> facts = new LinkedHashMap<>();
		StringBuilder summary = new StringBuilder();
		StringBuilder description = new StringBuilder();
		StringBuilder section = null;
		for (String line : markdown.split("\n")) {
			String text = line.strip();
			if (section == null && title == null && text.startsWith("# ")) {
				Matcher link = MARKDOWN_LINK.matcher(text.substring(2));
				if (link.find()) {
					title = link.group(1).strip();
					url = link.group(2);
				}
				else {
					title = text.substring(2).strip();
				}
			}
			else if (text.startsWith("## ")) {
				String heading = text.substring(3).strip().toLowerCase(Locale.ROOT);
				section = heading.startsWith("requirements") ? summary
						: heading.startsWith("full description") ? description : null;
			}
			else if (section != null) {
				section.append(line).append('\n');
			}
			else if (company == null && text.startsWith("**") && text.endsWith("**") && text.length() > 4) {
				company = text.substring(2, text.length() - 2).strip();
			}
			else {
				Matcher fact = FACT_LINE.matcher(text);
				if (fact.matches()) {
					facts.put(fact.group(1).strip(), fact.group(2).strip());
				}
			}
		}
		if (title == null || title.isBlank()) {
			throw new JobSourceException("HiringCafe answered in an unexpected format");
		}
		String link = facts.remove("View & apply");
		facts.remove("Job ID");
		LocalDate postedOn = parseDate(facts.get("Posted"));
		String cleanUrl;
		try {
			cleanUrl = SourceUrls.normalize((url != null) ? url : link);
		}
		catch (IllegalArgumentException ex) {
			cleanUrl = null;
		}
		return new JobDetails(externalId, title, company, cleanUrl, postedOn, facts,
				blankToNull(summary.toString().strip()), paragraphs(description.toString()));
	}

	/**
	 * Splits plain text into paragraphs at blank lines. Bullet characters on their own
	 * line are joined with the line after them.
	 */
	static List<String> paragraphs(String text) {
		List<String> paragraphs = new ArrayList<>();
		for (String block : text.split("\\n\\s*\\n")) {
			String paragraph = block.lines()
				.map(String::strip)
				.filter((line) -> !line.isEmpty())
				.collect(Collectors.joining("\n"));
			if (paragraph.isEmpty()) {
				continue;
			}
			if (!paragraphs.isEmpty() && paragraphs.getLast().equals("•")) {
				paragraphs.set(paragraphs.size() - 1, "• " + paragraph);
			}
			else {
				paragraphs.add(paragraph.startsWith("•\n") ? "• " + paragraph.substring(2) : paragraph);
			}
		}
		return List.copyOf(paragraphs);
	}

	/**
	 * The "**Posted:** 2026-09-07" line of each job in the search markdown, by job ID.
	 */
	static Map<String, LocalDate> postedDatesById(String markdown) {
		Map<String, LocalDate> dates = new HashMap<>();
		if (markdown == null) {
			return dates;
		}
		for (String section : markdown.split("\n### ")) {
			Matcher id = JOB_ID_LINE.matcher(section);
			Matcher posted = POSTED_LINE.matcher(section);
			if (id.find() && posted.find()) {
				LocalDate date = parseDate(posted.group(1));
				if (date != null) {
					dates.put(id.group(1), date);
				}
			}
		}
		return dates;
	}

	private static LocalDate parseDate(String text) {
		if (text == null) {
			return null;
		}
		try {
			return LocalDate.parse(text.strip());
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static String blankToNull(String text) {
		return (text == null || text.isBlank()) ? null : text;
	}

	/**
	 * Turns the query into {@code search_jobs} arguments. Only filters that are set are
	 * sent; the names and allowed values are the ones listed by {@code tools/list} on
	 * 2026-11-03.
	 */
	static Map<String, Object> arguments(JobSearchQuery query, int limit) {
		Map<String, Object> arguments = new LinkedHashMap<>();
		putIfPresent(arguments, "search", query.keywords());
		putIfPresent(arguments, "location", query.location());
		if (query.location() != null && query.radiusKm() != null) {
			arguments.put("radius_miles", Math.max(1, Math.round(query.radiusKm() / KM_PER_MILE)));
		}
		putValues(arguments, "workplace_types", query.workplaces());
		putValues(arguments, "seniority_levels", query.seniorities());
		putValues(arguments, "employment_types", query.employmentTypes());
		if (query.roleType() != null) {
			arguments.put("role_type", query.roleType().value());
		}
		putIfPresent(arguments, "posted_within_days", query.postedWithinDays());
		putIfPresent(arguments, "max_years_experience", query.maxYearsExperience());
		if (query.minPay() != null) {
			arguments.put("min_salary", query.minPay());
			arguments.put("salary_frequency", query.payPeriod().value());
		}
		if (query.publishedPayOnly()) {
			arguments.put("posted_salary_only", true);
		}
		putValues(arguments, "departments", query.departments());
		putValues(arguments, "industries", query.industries());
		putValues(arguments, "exclude_industries", query.excludedIndustries());
		if (query.companySize() != null) {
			putIfPresent(arguments, "min_employees", query.companySize().minEmployees());
			putIfPresent(arguments, "max_employees", query.companySize().maxEmployees());
		}
		putValues(arguments, "organization_types", query.organizationTypes());
		if (query.noDegreeRequired()) {
			arguments.put("bachelors_degree_requirement", List.of("Preferred", "Not Mentioned"));
		}
		putList(arguments, "language_requirements", query.languages());
		putList(arguments, "company_hq_countries", query.companyCountries());
		putList(arguments, "company_names", query.onlyCompanies());
		putList(arguments, "exclude_company_names", query.excludedCompanies());
		putValues(arguments, "benefits_and_perks", query.perks());
		if (query.quickApplyOnly()) {
			arguments.put("application_form_ease", List.of("Simple"));
		}
		putIfPresent(arguments, "max_per_company", query.jobsPerCompany());
		arguments.put("limit", limit);
		return arguments;
	}

	private static void putIfPresent(Map<String, Object> arguments, String name, Object value) {
		if (value != null) {
			arguments.put(name, value);
		}
	}

	private static void putValues(Map<String, Object> arguments, String name,
			Collection<? extends JobSearchQuery.Option> options) {
		if (!options.isEmpty()) {
			arguments.put(name, options.stream().map(JobSearchQuery.Option::value).toList());
		}
	}

	private static void putList(Map<String, Object> arguments, String name, List<String> values) {
		if (!values.isEmpty()) {
			arguments.put(name, values);
		}
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

	private static Optional<JobSearchResult> toResult(JobCard card, String summary, LocalDate postedOn) {
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
				locationFrom(highlights), highlights, url, summary, postedOn));
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
