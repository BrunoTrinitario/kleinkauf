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

// If the mail server fails, the request is not broken: it stays in the log and returns false
class MailServiceTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final MailService mailService = new MailService(mailSender);

	@Test
	void mailSent_returnsTrue() {
		assertTrue(mailService.sendMail("ana@mail.com", "Asunto", "Texto"));
	}

	@Test
	void mailServerDown_returnsFalseWithoutThrowing() {
		doThrow(new MailSendException("SMTP caido")).when(mailSender).send(any(SimpleMailMessage.class));

		assertFalse(mailService.sendMail("ana@mail.com", "Asunto", "Texto"));
	}
}
