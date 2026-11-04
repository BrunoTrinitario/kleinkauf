package com.kleinkauf.users.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/*
Sends the emails (today, the password recovery one). If the mail server fails it does not break the request: it leaves it in the
log as ERROR (with the cause, without the email content) and returns false. There are no retries.
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