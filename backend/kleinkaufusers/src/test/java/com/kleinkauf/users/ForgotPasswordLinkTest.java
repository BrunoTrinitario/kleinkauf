package com.kleinkauf.users;

import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.dtos.EmailDto;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.repositories.UserRepository;
import com.kleinkauf.users.services.MailService;
import com.kleinkauf.users.services.UserService;
import com.kleinkauf.users.util.JwtUtil;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
The recovery email carries a link to the store's frontend page (not to the API): there the new password is
requested and the frontend calls POST /api/v1/auth/forgot-password/{token}.
 */
@ExtendWith(MockitoExtension.class)
class ForgotPasswordLinkTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private MailService mailService;

	@Mock
	private JdbcTemplate jdbcTemplate;

	@InjectMocks
	private UserService userService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(userService, "platformScheme", "http");
		ReflectionTestUtils.setField(userService, "platformDomain", "localhost:2999");
	}

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	void linkGoesToTheStorePage() throws Exception {
		TenantContext.setCurrentTenant("mi_tienda");
		withUser();
		when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("mi_tienda")))
				.thenReturn(List.of("http://mi-tienda.localhost:2999"));

		userService.forgotPasswordRequest(new EmailDto("ana@mail.com"));

		assertEquals("http://mi-tienda.localhost:2999/reset-password?token=jwt-de-reset", sentLink());
	}

	@Test
	void withoutStore_linkGoesToThePlatform() throws Exception {
		withUser();
		when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("public"))).thenReturn(List.of());

		userService.forgotPasswordRequest(new EmailDto("ana@mail.com"));

		assertEquals("http://localhost:2999/reset-password?token=jwt-de-reset", sentLink());
	}

	@Test
	void unknownEmail_sendsNothing() throws Exception {
		assertFalse(userService.forgotPasswordRequest(new EmailDto("nadie@mail.com")));
		verify(mailService, never()).sendMail(anyString(), anyString(), anyString());
	}

	private void withUser() {
		User user = new User();
		user.setId(7L);
		user.setEmail("ana@mail.com");
		user.setPassword("hash-actual");
		when(userRepository.findByEmailIgnoreCase("ana@mail.com")).thenReturn(Optional.of(user));
		when(jwtUtil.generatePasswordToken(eq(7L), eq("ana@mail.com"), anyString(), eq("hash-actual"))).thenReturn("jwt-de-reset");
	}

	private String sentLink() throws Exception {
		ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
		verify(mailService).sendMail(eq("ana@mail.com"), anyString(), content.capture());
		String text = content.getValue();
		return text.substring(text.indexOf("http"));
	}
}
