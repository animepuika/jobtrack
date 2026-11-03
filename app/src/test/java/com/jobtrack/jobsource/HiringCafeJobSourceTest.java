package com.jobtrack.jobsource;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.jobtrack.jobsource.JobSearchQuery.Seniority;
import com.jobtrack.jobsource.JobSearchQuery.Workplace;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests the adapter against a small local HTTP server that plays HiringCafe, using
 * fictional responses. No real provider is contacted.
 */
class HiringCafeJobSourceTest {

	private HttpServer server;

	private final Deque<StubResponse> responses = new ArrayDeque<>();

	private final List<String> requestBodies = new ArrayList<>();

	private HiringCafeJobSource jobSource;

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/api/mcp", this::handle);
		this.server.start();
		URI endpoint = URI.create("http://127.0.0.1:" + this.server.getAddress().getPort() + "/api/mcp");
		this.jobSource = new HiringCafeJobSource(
				new HiringCafeProperties(true, endpoint, Duration.ofSeconds(2), Duration.ofMillis(500), 10));
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	@Test
	void mapsJobCardsIntoSearchResults() throws IOException {
		this.responses.add(new StubResponse(200, fixture("search-response.json"), null, 0));

		JobSearchPage page = this.jobSource
			.search(new JobSearchQuery("Java developer", "Latvia", Workplace.HYBRID, Seniority.ENTRY));

		assertThat(page.total()).isEqualTo(12);
		assertThat(page.results()).hasSize(2);
		JobSearchResult first = page.results().getFirst();
		assertThat(first.externalId()).isEqualTo("fixture00000001");
		assertThat(first.title()).isEqualTo("Junior Java Developer");
		assertThat(first.company()).isEqualTo("Example Logistics");
		assertThat(first.location()).isEqualTo("Riga, Riga");
		assertThat(first.highlights()).containsExactly("Riga, Riga · Hybrid", "30k–40k/yr", "Full Time · Entry Level");
		assertThat(first.url()).isEqualTo("https://hiringcafe.com/job/junior-java-developer-fixture00000001");
		assertThat(first.summary())
			.isEqualTo("Fictional summary: Java and Spring Boot with PostgreSQL; Docker is a plus.");
		assertThat(page.results().get(1).summary()).isEqualTo("Fictional summary: Python and Django.");
	}

	@Test
	void sendsAnMcpToolCall() throws IOException {
		this.responses.add(new StubResponse(200, fixture("search-response.json"), null, 0));

		this.jobSource.search(new JobSearchQuery("Java developer", "Latvia", Workplace.REMOTE, null));

		assertThat(this.requestBodies).singleElement()
			.satisfies((body) -> assertThat(body).contains("\"method\":\"tools/call\"")
				.contains("\"name\":\"search_jobs\"")
				.contains("\"search\":\"Java developer\"")
				.contains("\"location\":\"Latvia\"")
				.contains("\"workplace_types\":[\"Remote\"]")
				.doesNotContain("seniority_levels")
				.doesNotContain("exclude_company_names"));
	}

	@Test
	void leavesOutCompaniesAlreadyShownForTheNextPage() throws IOException {
		this.responses.add(new StubResponse(200, fixture("search-response.json"), null, 0));

		this.jobSource.search(new JobSearchQuery("Java", null, null, null,
				List.of("Example Logistics", " Sample & Sons ", "Example Logistics")));

		assertThat(this.requestBodies).singleElement()
			.satisfies((body) -> assertThat(body)
				.contains("\"exclude_company_names\":[\"Example Logistics\",\"Sample & Sons\"]"));
	}

	@Test
	void reportsToolErrorsSentWithHttp200() {
		this.responses.add(new StubResponse(200, """
				{"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"Invalid filter"}],"isError":true}}
				""", null, 0));

		assertThatExceptionOfType(JobSourceException.class)
			.isThrownBy(() -> this.jobSource.search(new JobSearchQuery("Java", null, null, null)))
			.withMessageContaining("Invalid filter");
	}

	@Test
	void respectsRetryAfterAndDoesNotRetryWhenRateLimited() {
		this.responses.add(new StubResponse(429, "{}", "30", 0));

		assertThatExceptionOfType(JobSourceException.class)
			.isThrownBy(() -> this.jobSource.search(new JobSearchQuery("Java", null, null, null)))
			.satisfies((ex) -> assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofSeconds(30)));
		assertThat(this.requestBodies).hasSize(1);
	}

	@Test
	void retriesOnceAfterAServerError() throws IOException {
		this.responses.add(new StubResponse(503, "{}", null, 0));
		this.responses.add(new StubResponse(200, fixture("search-response.json"), null, 0));

		JobSearchPage page = this.jobSource.search(new JobSearchQuery("Java", null, null, null));

		assertThat(page.results()).hasSize(2);
		assertThat(this.requestBodies).hasSize(2);
	}

	@Test
	void givesUpAfterTwoTimeouts() {
		this.responses.add(new StubResponse(200, "{}", null, 1500));
		this.responses.add(new StubResponse(200, "{}", null, 1500));

		assertThatExceptionOfType(JobSourceException.class)
			.isThrownBy(() -> this.jobSource.search(new JobSearchQuery("Java", null, null, null)))
			.withMessage("HiringCafe did not respond in time");
	}

	@Test
	void reportsAConnectionFailureDifferentlyFromATimeout() {
		this.server.stop(0);

		assertThatExceptionOfType(JobSourceException.class)
			.isThrownBy(() -> this.jobSource.search(new JobSearchQuery("Java", null, null, null)))
			.withMessageStartingWith("HiringCafe could not be reached");
	}

	@Test
	void rejectsAnUnexpectedResponseShape() {
		this.responses.add(new StubResponse(200, """
				{"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"markdown only"}]}}
				""", null, 0));

		assertThatExceptionOfType(JobSourceException.class)
			.isThrownBy(() -> this.jobSource.search(new JobSearchQuery("Java", null, null, null)))
			.withMessage("HiringCafe answered in an unexpected format");
	}

	@Test
	void readsLocationFromDisplayText() {
		assertThat(HiringCafeJobSource.locationFrom(List.of("Europe · Remote"))).isEqualTo("Europe");
		assertThat(HiringCafeJobSource.locationFrom(List.of("Riga"))).isEqualTo("Riga");
		assertThat(HiringCafeJobSource.locationFrom(List.of())).isNull();
	}

	private void handle(HttpExchange exchange) throws IOException {
		try (InputStream body = exchange.getRequestBody()) {
			this.requestBodies.add(new String(body.readAllBytes(), StandardCharsets.UTF_8));
		}
		StubResponse response = this.responses.poll();
		if (response == null) {
			response = new StubResponse(500, "{}", null, 0);
		}
		if (response.delayMillis() > 0) {
			try {
				Thread.sleep(response.delayMillis());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
		byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
		if (response.retryAfter() != null) {
			exchange.getResponseHeaders().add("Retry-After", response.retryAfter());
		}
		try {
			exchange.sendResponseHeaders(response.status(), bytes.length);
			exchange.getResponseBody().write(bytes);
		}
		catch (IOException ex) {
			// the client gave up waiting, which is what the timeout test expects
		}
		finally {
			exchange.close();
		}
	}

	private static String fixture(String name) throws IOException {
		try (InputStream in = HiringCafeJobSourceTest.class.getResourceAsStream("/hiringcafe/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private record StubResponse(int status, String body, String retryAfter, long delayMillis) {
	}

}
