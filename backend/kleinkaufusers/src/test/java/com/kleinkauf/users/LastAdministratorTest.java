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
La tienda no se puede quedar sin ADMINISTRADOR: el ultimo no se borra (ni a si mismo con DELETE /auth/me ni desde
el panel) ni pierde el rol. Contra una base real (H2) para que los borrados logicos no cuenten como administradores.
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

	// Lo pide el CommandLineRunner de KleinKaufusersApplication; el servicio que se prueba se arma a mano
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
	void unicoAdministrador_noSePuedeBorrar() {
		User owner = user("dueno@t.com", "ADMINISTRADOR", "EMPLEADO");
		user("cliente@t.com", "CLIENTE");

		ApiException e = assertThrows(ApiException.class, () -> userService.deleteUserById(owner.getId()));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertEquals(ErrorMessages.LAST_ADMINISTRATOR, e.getMessage());
		assertTrue(userRepository.findById(owner.getId()).isPresent());
	}

	@Test
	void unicoAdministrador_noPierdeElRol() {
		User owner = user("dueno@t.com", "ADMINISTRADOR", "EMPLEADO");

		ApiException e = assertThrows(ApiException.class, () -> userService.deleteRoleFromUser(owner.getId(), "ADMINISTRADOR"));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
		assertTrue(roleRepository.findByUser_IdAndType(owner.getId(), "ADMINISTRADOR").isPresent());
	}

	@Test
	void unicoAdministrador_puedePerderOtroRol() throws ApiException {
		User owner = user("dueno@t.com", "ADMINISTRADOR", "EMPLEADO");

		assertTrue(userService.deleteRoleFromUser(owner.getId(), "EMPLEADO"));
	}

	@Test
	void conOtroAdministrador_sePuedeBorrarOSacarElRol() throws ApiException {
		User owner = user("dueno@t.com", "ADMINISTRADOR");
		User partner = user("socio@t.com", "ADMINISTRADOR");

		assertTrue(userService.deleteRoleFromUser(partner.getId(), "ADMINISTRADOR"));
		em.flush();
		// Ahora el dueno es el unico
		assertThrows(ApiException.class, () -> userService.deleteUserById(owner.getId()));
	}

	@Test
	void unAdministradorBorrado_noCuenta() {
		User owner = user("dueno@t.com", "ADMINISTRADOR");
		User deleted = em.find(User.class, user("borrado@t.com", "ADMINISTRADOR").getId());
		deleted.setDeletedAt(LocalDateTime.now());
		em.flush();
		em.clear();

		ApiException e = assertThrows(ApiException.class, () -> userService.deleteUserById(owner.getId()));

		assertEquals(HttpStatus.CONFLICT, e.getStatus());
	}

	@Test
	void unCliente_seBorraAunqueHayaUnSoloAdministrador() throws ApiException {
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
		// El servicio lo vuelve a leer de la base, con sus roles
		em.flush();
		em.clear();
		return user;
	}
}
