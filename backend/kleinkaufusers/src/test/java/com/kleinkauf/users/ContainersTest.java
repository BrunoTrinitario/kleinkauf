package com.kleinkauf.users;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/*
Base de los tests que levantan la app entera (@SpringBootTest): Postgres y Redis reales con Testcontainers, en vez de
depender de que haya un Postgres en localhost:5432. @ServiceConnection le pasa las URLs a Spring.
Los contenedores se levantan una sola vez para todas las clases (Testcontainers los borra al terminar): Spring reusa
el contexto entre clases de test, y si cada clase tuviera los suyos, la segunda apuntaria a uno ya apagado.
Sin Docker estos tests se saltean (no fallan): los demas tests no lo necesitan.
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
