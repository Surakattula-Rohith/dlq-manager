package com.dlqmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class DlqManagerApplication {

	public static void main(String[] args) {
		// Configure JVM to use UTC timezone for consistent timestamp handling
		// Ensures compatibility with PostgreSQL which recognizes UTC universally
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		SpringApplication.run(DlqManagerApplication.class, args);
	}

}
