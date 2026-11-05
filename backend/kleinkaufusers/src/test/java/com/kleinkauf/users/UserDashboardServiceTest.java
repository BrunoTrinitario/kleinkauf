package com.kleinkauf.users;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.dtos.dashboard.UserDashboardDto;
import com.kleinkauf.users.dtos.platform.PeriodCountDto;
import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.services.UserDashboardService;
import com.kleinkauf.users.services.UserService;
import com.kleinkauf.users.util.DashboardTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
Store users dashboard against a real database (H2 in PostgreSQL mode), without multi-tenancy:
validates the JPQL queries and that deleted ones do not count.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:users-dashboard;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		// Tests run in UTC: with the same zone the stored days are the dashboard's (DashboardTimeTest tests the other one)
		"users.dashboard.zone=UTC"
})
@Import({UserDashboardService.class, DashboardTime.class})
class UserDashboardServiceTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private UserDashboardService dashboardService;

	// Required by KleinKaufusersApplication's CommandLineRunner
	@MockitoBean
	private UserService userService;

	@Test
	void countsTotalActiveAndByRole_withoutDeleted() throws ApiException {
		LocalDateTime now = LocalDateTime.now();
		user("admin@t.com", now, now.minusDays(1), "ADMINISTRADOR", "EMPLEADO");
		user("empleado@t.com", now, now.minusDays(40), "EMPLEADO");
		user("cliente@t.com", now, null, "CLIENTE");
		User deleted = user("borrado@t.com", now, now, "CLIENTE");
		deleted.setDeletedAt(now);
		em.flush();
		em.clear();

		UserDashboardDto dashboard = dashboardService.dashboard(null, null, null);

		assertEquals(3, dashboard.total());
		// Logged in within the last 30 days: only the administrator (the deleted one does not count)
		assertEquals(1, dashboard.active());
		assertEquals(30, dashboard.activeWindowDays());
		assertEquals(Map.of("ADMINISTRADOR", 1L, "EMPLEADO", 2L, "CLIENTE", 1L), dashboard.byRole());
	}

	@Test
	void withoutUsers_rolesAreZero() throws ApiException {
		UserDashboardDto dashboard = dashboardService.dashboard(null, null, null);

		assertEquals(0, dashboard.total());
		assertEquals(List.of("ADMINISTRADOR", "EMPLEADO", "CLIENTE"), List.copyOf(dashboard.byRole().keySet()));
		assertEquals(30, dashboard.registrations().periods().size());
	}

	@Test
	void signUpsPerPeriod_includeTheWholeLastDay() throws ApiException {
		user("a@t.com", LocalDateTime.of(2026, 10, 5, 0, 0), null, "CLIENTE");
		user("b@t.com", LocalDateTime.of(2026, 10, 6, 23, 59), null, "CLIENTE");
		user("c@t.com", LocalDateTime.of(2026, 10, 12, 10, 0), null, "CLIENTE");
		// Outside the range
		user("d@t.com", LocalDateTime.of(2026, 10, 13, 0, 0), null, "CLIENTE");
		em.flush();

		UserDashboardDto dashboard = dashboardService.dashboard("2026-10-05", "2026-10-12", "WEEK");

		// Weeks starting on Monday: 10/5 and 10/12
		assertEquals(List.of(
				new PeriodCountDto(LocalDate.of(2026, 10, 5), 2),
				new PeriodCountDto(LocalDate.of(2026, 10, 12), 1)), dashboard.registrations().periods());
	}

	@Test
	void invalidPeriod_400() {
		ApiException e = assertThrows(ApiException.class, () -> dashboardService.dashboard("2026-10-10", "2026-10-01", null));
		assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
	}

	private User user(String email, LocalDateTime createdAt, LocalDateTime lastLogin, String... roles) {
		User user = new User();
		user.setName(email);
		user.setEmail(email);
		user.setPassword("x");
		user.setCreatedAt(createdAt);
		user.setLastLogin(lastLogin);
		em.persist(user);
		for (String type : roles) {
			Role role = new Role();
			role.setType(type);
			role.setUser(user);
			em.persist(role);
		}
		return user;
	}
}
