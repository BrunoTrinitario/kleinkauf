package com.kleinkauf.product;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.common.security.InternalTokenService;
import com.kleinkauf.product.services.UsersClient;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
Levanta solo UsersClient con los aspectos reales de Resilience4j (sin base de datos)
para comprobar retry, fallback y que los 4xx no se reintentan.
 */
@SpringBootTest(
		classes = UsersClient.class,
		properties = {
				"users.url=http://users/",
				"resilience4j.retry.instances.users.max-attempts=3",
				"resilience4j.retry.instances.users.wait-duration=1ms",
				"resilience4j.retry.instances.users.ignore-exceptions=org.springframework.web.client.HttpClientErrorException",
				"resilience4j.circuitbreaker.instances.users.ignore-exceptions=org.springframework.web.client.HttpClientErrorException"
		})
@ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class})
class UsersClientResilienceTest {

	@Autowired
	private UsersClient usersClient;

	@MockitoBean
	private RestTemplate restTemplate;

	@MockitoBean
	private InternalTokenService internalTokenService;

	@Test
	void usersCaido_reintenta3VecesYUsaElFallback() {
		when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), ArgumentMatchers.<ParameterizedTypeReference<List<RoleDto>>>any()))
				.thenThrow(new ResourceAccessException("connection refused"));

		assertNull(usersClient.getUserRoles(1L, "tienda1"));
		verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.GET), any(), ArgumentMatchers.<ParameterizedTypeReference<List<RoleDto>>>any());
	}

	@Test
	void error4xx_noSeReintentaYUsaElFallback() {
		when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TenantDto.class)))
				.thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

		assertNull(usersClient.getTenant("tienda1"));
		verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(), eq(TenantDto.class));
	}
}
