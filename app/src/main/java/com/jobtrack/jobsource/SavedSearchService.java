package com.jobtrack.jobsource;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's saved searches. Saving under a name that already exists replaces that
 * search, so a saved search can be updated by saving it again.
 */
@Service
@Transactional
public class SavedSearchService {

	static final int MAX_SEARCHES = 30;

	static final int MAX_NAME_LENGTH = 60;

	static final int MAX_QUERY_LENGTH = 4000;

	private static final Pattern SAFE_QUERY = Pattern.compile("[A-Za-z0-9=&%+._~*-]+");

	private final SavedSearchRepository searches;

	private final Clock clock;

	SavedSearchService(SavedSearchRepository searches, Clock clock) {
		this.searches = searches;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<SavedSearch> list(long ownerId) {
		return this.searches.findByOwnerIdOrderByNameAsc(ownerId);
	}

	/**
	 * Saves the search page's parameters under a name.
	 * @return {@code true} if it replaced a search with the same name
	 */
	public boolean save(long ownerId, String name, String query) {
		String cleanName = (name == null) ? "" : name.strip();
		if (cleanName.isEmpty()) {
			throw new IllegalArgumentException("Give the search a name");
		}
		if (cleanName.length() > MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("Keep the name under " + MAX_NAME_LENGTH + " characters");
		}
		if (!isSafeQuery(query)) {
			throw new IllegalArgumentException("This search can't be saved: run a search first");
		}
		var existing = this.searches.findByName(ownerId, cleanName);
		if (existing.isPresent()) {
			existing.get().replaceQuery(query);
			return true;
		}
		if (this.searches.countByOwnerId(ownerId) >= MAX_SEARCHES) {
			throw new IllegalArgumentException(
					"You have " + MAX_SEARCHES + " saved searches. Remove one before saving another.");
		}
		this.searches.save(new SavedSearch(ownerId, cleanName, query, Instant.now(this.clock)));
		return false;
	}

	/**
	 * True for URL-encoded address parameters only: letters, digits and the characters
	 * an encoder produces. Saved queries end up in links and redirects, so nothing else
	 * is accepted.
	 */
	public static boolean isSafeQuery(String query) {
		return query != null && !query.isBlank() && query.length() <= MAX_QUERY_LENGTH
				&& SAFE_QUERY.matcher(query).matches();
	}

	/**
	 * Removes one of the user's saved searches. Another user's search is left alone.
	 */
	public void delete(long ownerId, long id) {
		this.searches.findByIdAndOwnerId(id, ownerId).ifPresent(this.searches::delete);
	}

}
