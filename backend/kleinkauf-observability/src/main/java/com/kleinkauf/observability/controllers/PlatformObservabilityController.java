package com.kleinkauf.observability.controllers;

import com.kleinkauf.common.audit.AuditEvent;
import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.servlet.security.CustomUserDetails;
import com.kleinkauf.observability.audit.AuditCsv;
import com.kleinkauf.observability.audit.AuditStore;
import com.kleinkauf.observability.collector.PlatformState;
import com.kleinkauf.observability.dto.PageDto;
import com.kleinkauf.observability.dto.PlatformOverviewDto;
import com.kleinkauf.observability.dto.WsTicketDto;
import com.kleinkauf.observability.security.HeaderAuthFilter;
import com.kleinkauf.observability.security.RolesService;
import com.kleinkauf.observability.ws.TicketStore;
import com.kleinkauf.observability.ws.WsTicket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/*
Platform dashboard (SUPERADMIN only, see SecurityConfig). No store: from a store it responds 404,
like the rest of /api/v1/platform/**.
- POST /ws-ticket: ticket to open the WS. Issued with the current roles (requested from users right then):
  a deactivated superadmin does not get another one.
- GET /overview: the collector's last reading, to show something before the first WS message.
- GET /audit: the platform audit (stores, superadmins), paginated and with filters. It does not include the
  stores' audit: only each administrator sees that one.
- GET /audit/export: the same as CSV (up to 50000 events).
 */
@RestController
@RequestMapping("/api/v1/platform/observability")
public class PlatformObservabilityController {

    private final TicketStore tickets;
    private final RolesService roles;
    private final PlatformState state;
    private final AuditStore audit;
    private final AuditCsv csv;

    public PlatformObservabilityController(TicketStore tickets, RolesService roles, PlatformState state, AuditStore audit,
                                           AuditCsv csv) {
        this.tickets = tickets;
        this.roles = roles;
        this.state = state;
        this.audit = audit;
        this.csv = csv;
    }

    @PostMapping("/ws-ticket")
    public ResponseEntity<WsTicketDto> ticket(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        requirePlatform(request);
        List<RoleDto> current = roles.fresh(user.getId(), TenantConstants.PUBLIC);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo verificar el acceso");
        }
        if (!RolesService.has(current, RolesService.SUPERADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        String ticket = tickets.issue(new WsTicket(user.getId(), TenantConstants.PUBLIC));
        return ResponseEntity.ok(new WsTicketDto(ticket, tickets.ttlSeconds()));
    }

    @GetMapping("/overview")
    public ResponseEntity<PlatformOverviewDto> overview(HttpServletRequest request) {
        requirePlatform(request);
        return ResponseEntity.ok(state.overview());
    }

    @GetMapping("/audit")
    public ResponseEntity<PageDto<AuditEvent>> audit(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + AuditQueries.DEFAULT_SIZE) int size
    ) {
        requirePlatform(request);
        return ResponseEntity.ok(audit.page(TenantConstants.PUBLIC,
                AuditQueries.query(from, to, userId, entity, entityId, action), page, size));
    }

    @GetMapping("/audit/export")
    public ResponseEntity<byte[]> export(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action
    ) {
        requirePlatform(request);
        return AuditQueries.export(audit, csv, TenantConstants.PUBLIC,
                AuditQueries.query(from, to, userId, entity, entityId, action), "plataforma");
    }

    private static void requirePlatform(HttpServletRequest request) {
        if (!TenantConstants.PUBLIC.equals(HeaderAuthFilter.tenantOf(request))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
