package com.kleinkauf.users.dtos;

// Report from the store footer. email is optional (with an open session the account's is used)
public record NewBugReportDto(
        String description,
        String email,
        String pageUrl
) {
}
