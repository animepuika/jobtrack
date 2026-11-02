package com.jobtrack.matching;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Builds small, real PDF and Word files with fictional CV text for tests.
 */
public final class TestCvFiles {

	private TestCvFiles() {
	}

	public static byte[] pdf(List<String> lines) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
				content.setLeading(14);
				content.newLineAtOffset(50, 720);
				for (String line : lines) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
			document.save(out);
			return out.toByteArray();
		}
	}

	public static byte[] docx(List<String> paragraphs) throws IOException {
		StringBuilder xml = new StringBuilder(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
		for (String paragraph : paragraphs) {
			xml.append("<w:p><w:r><w:t>")
				.append(paragraph.replace("&", "&amp;").replace("<", "&lt;"))
				.append("</w:t></w:r></w:p>");
		}
		xml.append("</w:body></w:document>");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("word/document.xml"));
			zip.write(xml.toString().getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return out.toByteArray();
	}

}
