package com.kleinkauf.users;

import com.kleinkauf.users.services.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

// Si el servidor de mail falla, no corta la request: queda en el log y devuelve false
class MailServiceTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final MailService mailService = new MailService(mailSender);

	@Test
	void mailMandado_devuelveTrue() {
		assertTrue(mailService.sendMail("ana@mail.com", "Asunto", "Texto"));
	}

	@Test
	void servidorDeMailCaido_devuelveFalseSinTirar() {
		doThrow(new MailSendException("SMTP caido")).when(mailSender).send(any(SimpleMailMessage.class));

		assertFalse(mailService.sendMail("ana@mail.com", "Asunto", "Texto"));
	}
}
