package com.jobtrack.documents;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param directory folder for uploaded files; keep it outside Git and backups you
 * share
 * @param maxSize largest CV file accepted
 */
@ConfigurationProperties("jobtrack.documents")
public record DocumentProperties(@DefaultValue("data/documents") Path directory,
		@DefaultValue("5MB") DataSize maxSize) {

}
