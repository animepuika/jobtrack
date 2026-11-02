package com.jobtrack.matching;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reads the plain text of a CV, in memory only. PDFs are read with Apache PDFBox. A
 * Word .docx file is a zip of XML files, so its text is taken from
 * {@code word/document.xml} with the JDK alone. Output is capped, so a crafted file
 * cannot make the server unpack gigabytes.
 */
public final class CvTextExtractor {

	private static final int MAX_TEXT_BYTES = 10 * 1024 * 1024;

	private CvTextExtractor() {
	}

	public static String extract(byte[] file, String contentType) throws IOException {
		if ("application/pdf".equals(contentType)) {
			return pdf(file);
		}
		if (contentType != null && contentType.contains("wordprocessingml")) {
			return docx(file);
		}
		throw new IllegalArgumentException("Only PDF and Word files can be read");
	}

	private static String pdf(byte[] file) throws IOException {
		try (PDDocument document = Loader.loadPDF(file)) {
			return new PDFTextStripper().getText(document);
		}
	}

	private static String docx(byte[] file) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if ("word/document.xml".equals(entry.getName())) {
					String xml = readCapped(zip);
					return xml.replaceAll("</w:p>", "\n")
						.replaceAll("<w:tab/>", " ")
						.replaceAll("<[^>]+>", "")
						.replace("&amp;", "&")
						.replace("&lt;", "<")
						.replace("&gt;", ">")
						.replace("&quot;", "\"")
						.replace("&apos;", "'");
				}
			}
		}
		throw new IllegalArgumentException("That Word file has no document text");
	}

	private static String readCapped(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) != -1) {
			if (out.size() + read > MAX_TEXT_BYTES) {
				throw new IllegalArgumentException("That Word file is too large to read");
			}
			out.write(buffer, 0, read);
		}
		return out.toString(StandardCharsets.UTF_8);
	}

}
