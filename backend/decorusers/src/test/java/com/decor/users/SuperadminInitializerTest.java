package com.decor.users;

import com.decor.users.config.SuperadminInitializer;
import com.decor.users.entities.Role;
import com.decor.users.entities.User;
import com.decor.users.repositories.RoleRepository;
import com.decor.users.repositories.UserRepository;
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
		config("root@decor.com", "clave");
		when(userRepository.existsByEmail("root@decor.com")).thenReturn(true);

		initializer.run(null);

		verify(userRepository, never()).save(any());
		verifyNoInteractions(roleRepository);
	}

	@Test
	void creaElSuperadminConSuRol() {
		config("root@decor.com", "clave");
		when(userRepository.existsByEmail("root@decor.com")).thenReturn(false);
		when(passwordEncoder.encode("clave")).thenReturn("hash");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		initializer.run(null);

		ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
		ArgumentCaptor<Role> role = ArgumentCaptor.forClass(Role.class);
		verify(userRepository).save(user.capture());
		verify(roleRepository).save(role.capture());
		assertEquals("root@decor.com", user.getValue().getEmail());
		assertEquals("hash", user.getValue().getPassword());
		assertEquals("SUPERADMIN", role.getValue().getType());
		assertSame(user.getValue(), role.getValue().getUser());
	}

	private void config(String email, String password) {
		ReflectionTestUtils.setField(initializer, "email", email);
		ReflectionTestUtils.setField(initializer, "password", password);
	}
}
