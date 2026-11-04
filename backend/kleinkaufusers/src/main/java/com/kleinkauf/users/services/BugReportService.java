package com.kleinkauf.users.services;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.constants.ErrorMessages;
import com.kleinkauf.users.dtos.NewBugReportDto;
import com.kleinkauf.users.dtos.platform.BugReportDto;
import com.kleinkauf.users.dtos.platform.BugReportPageDto;
import com.kleinkauf.users.entities.BugReport;
import com.kleinkauf.users.entities.Tenant;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.repositories.BugReportRepository;
import com.kleinkauf.users.repositories.TenantRepository;
import com.kleinkauf.users.repositories.UserRepository;
import com.kleinkauf.users.util.Dates;
import com.kleinkauf.users.util.UserValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/*
"Reportar un bug" del footer de las tiendas (docs/backend/users.md):
- Alta: publica, con o sin sesion. La tienda sale del tenant de la request; el usuario, del X-User-Id del gateway.
- Panel de la plataforma: listado por estado (pending, resolved o all) y marcar como resuelto o pendiente.
 */
@Service
public class BugReportService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private BugReportRepository bugReportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    public void create(NewBugReportDto dto, Long userId, String userAgent) throws ApiException {
        String description = dto == null || dto.description() == null ? "" : dto.description().trim();
        if (description.length() < BugReport.DESCRIPTION_MIN_LENGTH || description.length() > BugReport.DESCRIPTION_MAX_LENGTH) {
            throw new ApiException(ErrorMessages.INVALID_BUG_REPORT, HttpStatus.BAD_REQUEST);
        }
        String email = dto.email() == null || dto.email().isBlank() ? null : dto.email();
        if (email != null) {
            UserValidator.validateEmail(email);
            email = UserValidator.normalizeEmail(email);
        }

        BugReport report = new BugReport();
        report.setTenantSchema(TenantContext.getCurrentTenant());
        report.setDescription(description);
        report.setPageUrl(truncate(dto.pageUrl(), BugReport.PAGE_URL_MAX_LENGTH));
        report.setUserAgent(truncate(userAgent, BugReport.USER_AGENT_MAX_LENGTH));
        // Con la sesion abierta: quien es y, si no escribio otro email, el de su cuenta
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user != null) {
            report.setUserId(user.getId());
            report.setReporterName(user.getName());
            if (email == null) {
                email = user.getEmail();
            }
        }
        report.setEmail(email);
        bugReportRepository.save(report);
    }

    public BugReportPageDto list(String status, Integer page, Integer size) throws ApiException {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorMessages.INVALID_PAGE, HttpStatus.BAD_REQUEST);
        }
        PageRequest pageable = PageRequest.of(pageNumber, pageSize);
        String filter = status == null || status.isBlank() ? "pending" : status.trim().toLowerCase(Locale.ROOT);
        Page<BugReport> result = switch (filter) {
            case "pending" -> bugReportRepository.findByResolvedAtIsNullOrderByCreatedAtDescIdDesc(pageable);
            case "resolved" -> bugReportRepository.findByResolvedAtIsNotNullOrderByCreatedAtDescIdDesc(pageable);
            case "all" -> bugReportRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
            default -> throw new ApiException(String.format(ErrorMessages.INVALID_FILTER, "status"), HttpStatus.BAD_REQUEST);
        };

        // URL de cada tienda: tambien las borradas, el reporte sigue diciendo de donde vino
        Map<String, String> urls = new HashMap<>();
        for (Tenant tenant : tenantRepository.findAll()) {
            urls.put(tenant.getSchema(), tenant.getUrl());
        }
        return new BugReportPageDto(
                result.getContent().stream().map(report -> toDto(report, urls)).toList(),
                pageNumber,
                pageSize,
                result.getTotalElements(),
                result.getTotalPages(),
                bugReportRepository.countByResolvedAtIsNull());
    }

    public BugReportDto setResolved(Long id, boolean resolved) throws ApiException {
        BugReport report = bugReportRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorMessages.BUG_REPORT_NOT_FOUND, HttpStatus.NOT_FOUND));
        // Marcarlo de nuevo como resuelto no cambia la fecha en que se resolvio
        if (resolved && report.getResolvedAt() == null) {
            report.setResolvedAt(LocalDateTime.now());
        } else if (!resolved) {
            report.setResolvedAt(null);
        }
        report = bugReportRepository.save(report);
        Map<String, String> urls = new HashMap<>();
        String schema = report.getTenantSchema();
        if (schema != null) {
            tenantRepository.findBySchemaAndDeletedAtIsNull(schema).ifPresent(tenant -> urls.put(schema, tenant.getUrl()));
        }
        return toDto(report, urls);
    }

    private static BugReportDto toDto(BugReport report, Map<String, String> urls) {
        String schema = report.getTenantSchema();
        return new BugReportDto(
                report.getId(),
                schema == null ? null : schema.replace('_', '-'),
                schema == null ? null : urls.get(schema),
                report.getUserId(),
                report.getReporterName(),
                report.getEmail(),
                report.getDescription(),
                report.getPageUrl(),
                report.getUserAgent(),
                Dates.withServerOffset(report.getCreatedAt()),
                Dates.withServerOffset(report.getResolvedAt()));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
