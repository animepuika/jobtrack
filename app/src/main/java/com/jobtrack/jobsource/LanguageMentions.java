package com.jobtrack.jobsource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds spoken languages a job's text mentions, such as "fluent German and English".
 * HiringCafe filters by language but doesn't return a job's languages, so this reads
 * them from the requirements text. Names must be capitalized, so "polish your code"
 * isn't Polish; a mention can still be about a market rather than a requirement,
 * which is why pages call these languages "mentioned".
 */
public final class LanguageMentions {

	private static final Map<String, String> NAMES = new LinkedHashMap<>();

	static {
		for (String name : List.of("English", "German", "French", "Spanish", "Italian", "Portuguese", "Dutch",
				"Latvian", "Lithuanian", "Estonian", "Russian", "Ukrainian", "Polish", "Czech", "Slovak", "Hungarian",
				"Romanian", "Bulgarian", "Croatian", "Serbian", "Slovenian", "Greek", "Turkish", "Finnish", "Swedish",
				"Norwegian", "Danish", "Icelandic", "Arabic", "Hebrew", "Hindi", "Urdu", "Bengali", "Persian",
				"Chinese", "Mandarin", "Cantonese", "Japanese", "Korean", "Vietnamese", "Thai", "Indonesian", "Malay",
				"Tagalog", "Catalan")) {
			NAMES.put(name, name);
		}
		NAMES.put("Farsi", "Persian");
	}

	private static final Pattern WORD = Pattern.compile("\\b(" + String.join("|", NAMES.keySet()) + ")\\b");

	private LanguageMentions() {
	}

	/**
	 * The languages mentioned, in the order they first appear, without duplicates.
	 */
	public static List<String> in(String text) {
		List<String> found = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return found;
		}
		Matcher matcher = WORD.matcher(text);
		while (matcher.find()) {
			String language = NAMES.get(matcher.group(1));
			if (!found.contains(language)) {
				found.add(language);
			}
		}
		return found;
	}

}
