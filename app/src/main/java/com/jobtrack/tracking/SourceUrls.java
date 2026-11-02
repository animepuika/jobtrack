package com.jobtrack.tracking;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Normalizes job links so the same listing is recognized when it is saved twice.
 */
public final class SourceUrls {

	private SourceUrls() {
	}

	/**
	 * Returns a normalized http(s) URL, or {@code null} for a missing link. The scheme
	 * and host are lower-cased, default ports and fragments are dropped, and
	 * {@code utm_*} tracking parameters are removed. Other query parameters are kept,
	 * because they can identify the listing.
	 */
	public static String normalize(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		URI uri;
		try {
			uri = new URI(url.strip());
		}
		catch (URISyntaxException ex) {
			throw new IllegalArgumentException("Source link is not a valid URL");
		}
		String scheme = (uri.getScheme() != null) ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
		if (!"http".equals(scheme) && !"https".equals(scheme) || uri.getHost() == null) {
			throw new IllegalArgumentException("Source link must be an http or https URL");
		}
		StringBuilder result = new StringBuilder(scheme).append("://").append(uri.getHost().toLowerCase(Locale.ROOT));
		boolean defaultPort = uri.getPort() == -1 || ("http".equals(scheme) && uri.getPort() == 80)
				|| ("https".equals(scheme) && uri.getPort() == 443);
		if (!defaultPort) {
			result.append(':').append(uri.getPort());
		}
		result.append((uri.getRawPath() == null || uri.getRawPath().isEmpty()) ? "/" : uri.getRawPath());
		String query = withoutTrackingParameters(uri.getRawQuery());
		if (!query.isEmpty()) {
			result.append('?').append(query);
		}
		return result.toString();
	}

	private static String withoutTrackingParameters(String rawQuery) {
		if (rawQuery == null) {
			return "";
		}
		return Arrays.stream(rawQuery.split("&"))
			.filter((parameter) -> !parameter.isEmpty())
			.filter((parameter) -> !parameter.toLowerCase(Locale.ROOT).startsWith("utm_"))
			.collect(Collectors.joining("&"));
	}

}
