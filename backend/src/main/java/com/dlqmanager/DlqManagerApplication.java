package com.dlqmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class DlqManagerApplication {

	public static void main(String[] args) {
		// Fix timezone issue: Force JVM to use UTC before anything else loads
		// This prevents "Asia/Calcutta" (old name) from being sent to PostgreSQL
		// PostgreSQL only recognizes "Asia/Kolkata" (new name) or UTC
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		SpringApplication.run(DlqManagerApplication.class, args);
	}

}
