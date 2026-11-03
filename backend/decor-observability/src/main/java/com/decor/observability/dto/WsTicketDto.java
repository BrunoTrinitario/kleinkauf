package com.decor.observability.dto;

// Ticket para abrir el WS: /ws?ticket=... Sirve una sola vez y vence en expiresIn segundos
public record WsTicketDto(String ticket, long expiresIn) {
}
