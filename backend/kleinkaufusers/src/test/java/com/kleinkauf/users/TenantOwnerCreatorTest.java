package com.kleinkauf.users;

import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.services.TenantOwnerCreator;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.hibernate.SessionBuilder;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantOwnerCreatorTest {

	@Mock
	private EntityManagerFactory entityManagerFactory;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private SessionFactory sessionFactory;

	@Mock
	private SessionBuilder sessionBuilder;

	@Mock
	private Session session;

	@Mock
	private Transaction transaction;

	@InjectMocks
	private TenantOwnerCreator creator;

	@BeforeEach
	void setUp() {
		when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
		when(sessionFactory.withOptions()).thenReturn(sessionBuilder);
		when(sessionBuilder.tenantIdentifier((Object) "mi_tienda")).thenReturn(sessionBuilder);
		when(sessionBuilder.openSession()).thenReturn(session);
		when(session.beginTransaction()).thenReturn(transaction);
	}

	@Test
	void creaAlDuenioEnElSchemaDelTenantComoAdministradorYEmpleado() {
		when(passwordEncoder.encode("secreta")).thenReturn("hash");

		creator.createOwner("mi_tienda", "Ana", "ana@mail.com", "secreta");

		// La sesion se abre atada al schema del tenant nuevo
		verify(sessionBuilder).tenantIdentifier((Object) "mi_tienda");
		ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
		verify(session, times(3)).persist(persisted.capture());
		User owner = (User) persisted.getAllValues().get(0);
		assertEquals("ana@mail.com", owner.getEmail());
		assertEquals("hash", owner.getPassword());
		List<String> roles = persisted.getAllValues().stream()
				.filter(Role.class::isInstance)
				.map(r -> ((Role) r).getType())
				.collect(Collectors.toList());
		assertEquals(List.of("ADMINISTRADOR", "EMPLEADO"), roles);
		verify(transaction).commit();
		verify(session).close();
	}

	@Test
	void siFallaHaceRollback() {
		when(transaction.isActive()).thenReturn(true);
		doThrow(new RuntimeException("db")).when(session).persist(any(User.class));

		assertThrows(RuntimeException.class, () -> creator.createOwner("mi_tienda", "Ana", "ana@mail.com", "secreta"));

		verify(transaction).rollback();
		verify(transaction, never()).commit();
		verify(session).close();
	}
}
