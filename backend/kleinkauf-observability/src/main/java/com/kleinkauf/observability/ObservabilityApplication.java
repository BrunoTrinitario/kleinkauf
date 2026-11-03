package com.kleinkauf.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling: el recolector y la revision de los accesos de los WS abiertos (Schedules)
@SpringBootApplication
@EnableScheduling
public class ObservabilityApplication {

	public static void main(String[] args) {
		SpringApplication.run(ObservabilityApplication.class, args);
	}
}
