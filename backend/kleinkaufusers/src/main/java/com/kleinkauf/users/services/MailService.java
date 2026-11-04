package com.kleinkauf.users.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/*
Manda los mails (hoy, el del recupero de contrasena). Si el servidor de mail falla no corta la request: lo deja en el
log como ERROR (con la causa, sin el contenido del mail) y devuelve false. No hay reintentos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    public boolean sendMail(String to, String subject, String text) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.error("No se pudo mandar el mail '{}' a {}: {}", subject, to, e.getMessage(), e);
            return false;
        }
    }
}