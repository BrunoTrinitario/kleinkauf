package com.kleinkauf.observability.dto;

// Ticket to open the WS: /ws?ticket=... Works only once and expires in expiresIn seconds
public record WsTicketDto(String ticket, long expiresIn) {
}
