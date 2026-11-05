package com.kleinkauf.sales.dto;

// Guest lookup: by POST so the email does not end up in the URL or the logs
public record LookupDto(
        String email,
        String number
) {
}
