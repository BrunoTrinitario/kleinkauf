package com.kleinkauf.users.controllers;

import com.kleinkauf.common.dto.mail.OrderConfirmationMailDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.services.OrderMailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Emails other services ask for (internal token, store in X-Forwarded-Host). See docs/mail.md
@RestController
@RequestMapping("/api/internal/mail")
public class MSMailController {

    @Autowired
    private OrderMailService orderMailService;

    // 204 whether it was sent, already sent before or failed (the failure stays in the log): sales does not retry
    @PostMapping("/order-confirmation")
    public ResponseEntity<Void> orderConfirmation(@RequestBody OrderConfirmationMailDto order) throws ApiException {
        orderMailService.sendConfirmation(order);
        return ResponseEntity.noContent().build();
    }
}
