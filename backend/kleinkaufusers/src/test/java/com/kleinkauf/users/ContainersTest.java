package com.kleinkauf.users;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/*
Base for the tests that start the whole app (@SpringBootTest): real Postgres and Redis with Testcontainers, instead of
depending on a Postgres at localhost:5432. @ServiceConnection passes the URLs to Spring.
The containers are started only once for all classes (Testcontainers removes them at the end): Spring reuses
the context across test classes, and if each class had its own, the second one would point to one already stopped.
Without Docker these tests are skipped (they do not fail): the other tests do not need it.
 */
@Testcontainers(disabledWithoutDocker = true)
abstract class ContainersTest {

	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@ServiceConnection(name = "redis")
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	static {
		if (DockerClientFactory.instance().isDockerAvailable()) {
			POSTGRES.start();
			REDIS.start();
		}
	}
}
