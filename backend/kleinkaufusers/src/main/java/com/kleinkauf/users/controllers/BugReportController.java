package com.kleinkauf.users.controllers;

import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.dtos.NewBugReportDto;
import com.kleinkauf.users.services.BugReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
"Report a bug" from the store footer: public, with or without a session. With a session, the gateway sends the
user in X-User-Id (optional login, like creating an order). Superadmins read them at /api/v1/platform/admin/bug-reports
 */
@RestController
@RequestMapping("/api/v1/bug-reports")
public class BugReportController {

    @Autowired
    private BugReportService bugReportService;

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody NewBugReportDto dto,
                                       @RequestHeader(value = Headers.USER_ID, required = false) Long userId,
                                       @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent)
            throws ApiException {
        bugReportService.create(dto, userId, userAgent);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
