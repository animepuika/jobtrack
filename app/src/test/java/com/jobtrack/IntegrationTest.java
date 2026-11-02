package com.jobtrack;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * A test that starts the whole application against a real PostgreSQL. Live
 * HiringCafe calls are off, so tests never depend on the internet.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "jobtrack.hiringcafe.enabled=false")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {

}
