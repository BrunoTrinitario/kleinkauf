package com.kleinkauf.users;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.constants.ErrorMessages;
import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.repositories.UserRepository;
import com.kleinkauf.users.services.RolePermissionService;
import com.kleinkauf.users.services.UserService;
import com.kleinkauf.users.util.CacheUtil;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/*
The store cannot be left without an ADMINISTRADOR: the last one is not deleted (neither by themselves with DELETE /auth/me nor from
the dashboard) nor does it lose the role. Against a real database (H2) so soft-deleted users do not count as administrators.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:last-admin;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class LastAdministratorTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	// Required by KleinKaufusersApplication's CommandLineRunner; the service under test is built by hand
	@MockitoBean
	private UserService springUserService;

	private final UserService userService = new UserService();

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(userService, "userRepository", userRepository);
		ReflectionTestUtils.setField(userService, "roleRepository", roleRepository);
		ReflectionTestUtils.setField(userService, "cacheUtil", mock(CacheUtil.class));
		ReflectionTestUtils.setField(userService, "rolePermissionService", mock(RolePermissionService.class));
	}

	@Test
	void onlyAdministrator_cannotBeDeleted() {
		User owner = user("dueno@t.com", "ADMINISTRADOR", "EMPLEADO");
		user("cliente@t.com", "CLIENTE");

		ApiException e = assertThrows(ApiException.class, () -> userService.deleteUserById(owner.getId()));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertEquals(ErrorMessages.LAST_ADMINISTRATOR, e.getMessage());
		assertTrue(userRepository.findById(owner.getId()).isPresent());
	}

	@Test
	void onlyAdministrator_doesNotLoseTheRole() {
		User owner = user("dueno@t.com", "ADMINISTRADOR", "EMPLEADO");

		ApiException e = assertThrows(ApiException.class, () -> userService.deleteRoleFromUser(owner.getId(), "ADMINISTRADOR"));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertTrue(roleRepository.findByUser_IdAndType(owner.getId(), "ADMINISTRADOR").isPresent());
	}

	@Test
	void onlyAdministrator_canLoseAnotherRole() throws ApiException {
		User owner = user("dueno@t.com", "ADMINISTRADOR", "EMPLEADO");

		assertTrue(userService.deleteRoleFromUser(owner.getId(), "EMPLEADO"));
	}

	@Test
	void withAnotherAdministrator_canBeDeletedOrLoseTheRole() throws ApiException {
		User owner = user("dueno@t.com", "ADMINISTRADOR");
		User partner = user("socio@t.com", "ADMINISTRADOR");

		assertTrue(userService.deleteRoleFromUser(partner.getId(), "ADMINISTRADOR"));
		em.flush();
		// Now the owner is the only one
		assertThrows(ApiException.class, () -> userService.deleteUserById(owner.getId()));
	}

	@Test
	void deletedAdministrator_doesNotCount() {
		User owner = user("dueno@t.com", "ADMINISTRADOR");
		User deleted = em.find(User.class, user("borrado@t.com", "ADMINISTRADOR").getId());
		deleted.setDeletedAt(LocalDateTime.now());
		em.flush();
		em.clear();

		ApiException e = assertThrows(ApiException.class, () -> userService.deleteUserById(owner.getId()));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
	}

	@Test
	void customer_isDeletedEvenIfThereIsOnlyOneAdministrator() throws ApiException {
		user("dueno@t.com", "ADMINISTRADOR");
		User client = user("cliente@t.com", "CLIENTE");

		assertTrue(userService.deleteUserById(client.getId()));
	}

	private User user(String email, String... roles) {
		User user = new User();
		user.setName(email);
		user.setEmail(email);
		user.setPassword("x");
		user.setCreatedAt(LocalDateTime.now());
		em.persist(user);
		for (String type : roles) {
			Role role = new Role();
			role.setType(type);
			role.setUser(user);
			em.persist(role);
		}
		// The service reads it again from the database, with its roles
		em.flush();
		em.clear();
		return user;
	}
}
