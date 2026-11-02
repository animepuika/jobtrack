package com.jobtrack.documents;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where uploaded files are kept. The local version writes to a private folder; a
 * cloud version can keep them in private Azure Blob Storage without other code
 * changing.
 */
public interface DocumentStorage {

	void store(String key, InputStream content) throws IOException;

	InputStream open(String key) throws IOException;

	void delete(String key) throws IOException;

}
