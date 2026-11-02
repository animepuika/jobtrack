package com.jobtrack.documents;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uploads, lists and serves CV versions. Only PDF and Word (.docx) files are
 * accepted, checked by their first bytes as well as their name, and every read
 * checks that the file belongs to the signed-in user.
 */
@Service
@Transactional
public class CvService {

	private static final byte[] PDF_SIGNATURE = { '%', 'P', 'D', 'F', '-' };

	private static final byte[] ZIP_SIGNATURE = { 'P', 'K', 3, 4 };

	private final CvVersionRepository versions;

	private final DocumentStorage storage;

	private final DocumentProperties properties;

	private final Clock clock;

	public CvService(CvVersionRepository versions, DocumentStorage storage, DocumentProperties properties,
			Clock clock) {
		this.versions = versions;
		this.storage = storage;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @param content the file's bytes; the caller closes it
	 */
	public CvVersion upload(long ownerId, String label, String filename, long size, InputStream content) {
		String cleanLabel = (label == null) ? "" : label.strip();
		if (cleanLabel.isEmpty() || cleanLabel.length() > 100) {
			throw new IllegalArgumentException("Give the CV a name of up to 100 characters, like \"Backend CV, Nov 2026\"");
		}
		if (size <= 0) {
			throw new IllegalArgumentException("Choose a file to upload");
		}
		if (size > this.properties.maxSize().toBytes()) {
			throw new IllegalArgumentException(
					"The file is larger than " + this.properties.maxSize().toMegabytes() + " MB");
		}
		FileType type = FileType.fromName(filename);
		String key = UUID.randomUUID().toString();
		try (BufferedInputStream in = new BufferedInputStream(content)) {
			in.mark(8);
			byte[] start = in.readNBytes(type.signature.length);
			in.reset();
			if (!Arrays.equals(start, type.signature)) {
				throw new IllegalArgumentException("That file doesn't look like a real " + type.description);
			}
			this.storage.store(key, in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not store the file", ex);
		}
		try {
			return this.versions.save(new CvVersion(ownerId, cleanLabel, safeFilename(filename, type), type.contentType,
					size, key, Instant.now(this.clock)));
		}
		catch (RuntimeException ex) {
			deleteQuietly(key);
			throw ex;
		}
	}

	@Transactional(readOnly = true)
	public List<CvVersion> list(long ownerId) {
		return this.versions.findByOwnerIdOrderByUploadedAtDescIdDesc(ownerId);
	}

	@Transactional(readOnly = true)
	public CvVersion get(long ownerId, long id) {
		return this.versions.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new CvNotFoundException(id));
	}

	/**
	 * Opens the file for download. The caller closes the stream.
	 */
	@Transactional(readOnly = true)
	public CvFile open(long ownerId, long id) {
		CvVersion version = get(ownerId, id);
		try {
			return new CvFile(version, this.storage.open(version.getStorageKey()));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The stored file could not be read", ex);
		}
	}

	private void deleteQuietly(String key) {
		try {
			this.storage.delete(key);
		}
		catch (IOException ex) {
			// the database row was not saved, so a stray file is harmless
		}
	}

	/**
	 * Keeps only the last path segment and replaces characters that could confuse a
	 * download header.
	 */
	static String safeFilename(String filename, FileType type) {
		String name = (filename == null) ? "" : filename.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").strip();
		if (name.isEmpty() || name.length() > 200) {
			name = "cv" + type.extension;
		}
		return name;
	}

	public record CvFile(CvVersion version, InputStream content) {
	}

	enum FileType {

		PDF(".pdf", "application/pdf", PDF_SIGNATURE, "PDF"), DOCX(".docx",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document", ZIP_SIGNATURE,
				"Word document");

		final String extension;

		final String contentType;

		final byte[] signature;

		final String description;

		FileType(String extension, String contentType, byte[] signature, String description) {
			this.extension = extension;
			this.contentType = contentType;
			this.signature = signature;
			this.description = description;
		}

		static FileType fromName(String filename) {
			String name = (filename == null) ? "" : filename.toLowerCase(Locale.ROOT).strip();
			for (FileType type : values()) {
				if (name.endsWith(type.extension)) {
					return type;
				}
			}
			throw new IllegalArgumentException("Upload a PDF or a Word (.docx) file");
		}

	}

}
