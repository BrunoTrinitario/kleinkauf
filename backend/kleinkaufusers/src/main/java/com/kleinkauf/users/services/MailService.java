package com.kleinkauf.users.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/*
Sends the emails (password recovery and order confirmation) through SMTP (Resend in production, docs/mail.md).
Each email goes out as "<store name>" <mail.from.address> with the store owner as Reply-To (StoreMailIdentity).
If the mail server fails it does not break the request: it leaves it in the log as ERROR (with the cause, without
the email content) and returns false. There are no retries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final StoreMailIdentity storeMailIdentity;

    @Value("${mail.from.address:no-reply@mail.kleinkauf.site}")
    private String fromAddress;

    public boolean sendMail(String to, String subject, String text) {
        return sendMail(storeMailIdentity.current(), to, subject, text);
    }

    public boolean sendMail(StoreMailIdentity.Sender sender, String to, String subject, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, sender.name());
            if (sender.replyTo() != null) {
                helper.setReplyTo(sender.replyTo());
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            mailSender.send(message);
            return true;
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.error("No se pudo mandar el mail '{}' a {}: {}", subject, to, e.getMessage(), e);
            return false;
        }
    }
}
