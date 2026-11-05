package com.kleinkauf.users;

import com.kleinkauf.users.services.MailService;
import com.kleinkauf.users.services.StoreMailIdentity;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Emails go out with our address and the store's name, with the owner as Reply-To. If the mail server fails, the
// request is not broken: it stays in the log and returns false
class MailServiceTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final StoreMailIdentity identity = mock(StoreMailIdentity.class);
	private final MailService mailService = new MailService(mailSender, identity);

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(mailService, "fromAddress", "no-reply@mail.kleinkauf.site");
		when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
		when(identity.current()).thenReturn(
				new StoreMailIdentity.Sender("Lucía Deco", "duenia@mail.com", "https://lucia.kleinkauf.site"));
	}

	@Test
	void mailSent_fromTheStoreWithOwnerAsReplyTo() throws Exception {
		assertTrue(mailService.sendMail("ana@mail.com", "Asunto", "Texto"));

		MimeMessage sent = sentMessage();
		InternetAddress from = (InternetAddress) sent.getFrom()[0];
		assertEquals("no-reply@mail.kleinkauf.site", from.getAddress());
		assertEquals("Lucía Deco", from.getPersonal());
		assertEquals("duenia@mail.com", ((InternetAddress) sent.getReplyTo()[0]).getAddress());
		assertEquals("ana@mail.com", ((InternetAddress) sent.getAllRecipients()[0]).getAddress());
	}

	@Test
	void withoutOwner_noReplyTo() throws Exception {
		when(identity.current()).thenReturn(new StoreMailIdentity.Sender("KleinKauf", null, "https://kleinkauf.site"));

		assertTrue(mailService.sendMail("ana@mail.com", "Asunto", "Texto"));

		// Without Reply-To, getReplyTo() falls back to From
		MimeMessage sent = sentMessage();
		assertNull(sent.getHeader("Reply-To"));
	}

	@Test
	void mailServerDown_returnsFalseWithoutThrowing() {
		doThrow(new MailSendException("SMTP caido")).when(mailSender).send(any(MimeMessage.class));

		assertFalse(mailService.sendMail("ana@mail.com", "Asunto", "Texto"));
	}

	private MimeMessage sentMessage() {
		ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(message.capture());
		return message.getValue();
	}
}
