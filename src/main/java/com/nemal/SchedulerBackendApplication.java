package com.nemal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class SchedulerBackendApplication {

	public static void main(String[] args) {
		// All stored times are UTC; run the JVM on UTC so LocalDateTime.now(), @CreationTimestamp
		// and scheduled jobs agree on one clock regardless of the host's timezone.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(SchedulerBackendApplication.class, args);
	}

}
