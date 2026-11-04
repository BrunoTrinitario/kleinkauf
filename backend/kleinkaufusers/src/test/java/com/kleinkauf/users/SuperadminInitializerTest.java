package com.kleinkauf.users;

import com.kleinkauf.users.config.SuperadminInitializer;
import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperadminInitializerTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private RoleRepository roleRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private SuperadminInitializer initializer;

	@Test
	void sinConfiguracion_noCreaNada() {
		config("", "");

		initializer.run(null);

		verifyNoInteractions(userRepository, roleRepository);
	}

	@Test
	void siYaExiste_noLoVuelveACrear() {
		config("root@kleinkauf.com", "clave");
		when(userRepository.existsByEmailIgnoreCase("root@kleinkauf.com")).thenReturn(true);

		initializer.run(null);

		verify(userRepository, never()).save(any());
		verifyNoInteractions(roleRepository);
	}

	@Test
	void creaElSuperadminConSuRol() {
		config("root@kleinkauf.com", "clave");
		when(userRepository.existsByEmailIgnoreCase("root@kleinkauf.com")).thenReturn(false);
		when(passwordEncoder.encode("clave")).thenReturn("hash");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		initializer.run(null);

		ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
		ArgumentCaptor<Role> role = ArgumentCaptor.forClass(Role.class);
		verify(userRepository).save(user.capture());
		verify(roleRepository).save(role.capture());
		assertEquals("root@kleinkauf.com", user.getValue().getEmail());
		assertEquals("hash", user.getValue().getPassword());
		assertEquals("SUPERADMIN", role.getValue().getType());
		assertSame(user.getValue(), role.getValue().getUser());
	}

	private void config(String email, String password) {
		ReflectionTestUtils.setField(initializer, "email", email);
		ReflectionTestUtils.setField(initializer, "password", password);
	}
}
