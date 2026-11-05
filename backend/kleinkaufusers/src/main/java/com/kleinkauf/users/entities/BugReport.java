package com.kleinkauf.users.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/*
A bug report someone sends from a store's footer. It lives in public (explicit schema): superadmins read it
from the platform dashboard, even though it is sent with the store's tenant in the context.
tenantSchema: the store it was sent from. userId, reporterName: the user if they had an open session.
email: to reply to them; the one written in the form or, if none, their account's.
resolvedAt: null while it is pending.
 */
@Entity
@Table(name = "bug_reports", schema = "public")
@NoArgsConstructor
@Getter
@Setter
public class BugReport {

    public static final int DESCRIPTION_MIN_LENGTH = 10;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;
    public static final int PAGE_URL_MAX_LENGTH = 500;
    public static final int USER_AGENT_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_schema")
    private String tenantSchema;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "reporter_name")
    private String reporterName;

    private String email;

    @Column(nullable = false, length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "page_url", length = PAGE_URL_MAX_LENGTH)
    private String pageUrl;

    @Column(name = "user_agent", length = USER_AGENT_MAX_LENGTH)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
