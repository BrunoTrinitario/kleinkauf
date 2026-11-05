package com.kleinkauf.observability.controllers;

import com.kleinkauf.common.audit.AuditEvent;
import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.servlet.security.CustomUserDetails;
import com.kleinkauf.observability.audit.AuditCsv;
import com.kleinkauf.observability.audit.AuditStore;
import com.kleinkauf.observability.dto.PageDto;
import com.kleinkauf.observability.dto.StoreUsageDto;
import com.kleinkauf.observability.dto.StoreUsersDto;
import com.kleinkauf.observability.dto.WsTicketDto;
import com.kleinkauf.observability.security.HeaderAuthFilter;
import com.kleinkauf.observability.security.RolesService;
import com.kleinkauf.observability.store.StoreFeeds;
import com.kleinkauf.observability.ws.TicketStore;
import com.kleinkauf.observability.ws.WsTicket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/*
Store dashboard (ADMINISTRADOR only, see SecurityConfig). Always for the request's store: without a store
(from the platform) it responds 404. The superadmin does not see the stores' audit.
- POST /ws-ticket: ticket for the store's WS, with the current roles.
- GET /store/audit: the store's history, paginated and with filters (date, user, entity, action).
- GET /store/audit/{entity}/{entityId}: history of one specific thing (e.g. Product/12).
- GET /store/audit/export: the history with the same filters, as CSV (up to 50000 events).
- GET /store/users and /store/usage: the same that store.users and store.usage send, for when the dashboard is opened
  (the WS only sends what is new). 503 if Redis could not be read.
 */
@RestController
@RequestMapping("/api/v1/observability")
public class StoreObservabilityController {

    private final TicketStore tickets;
    private final RolesService roles;
    private final AuditStore audit;
    private final StoreFeeds storeFeeds;
    private final AuditCsv csv;

    public StoreObservabilityController(TicketStore tickets, RolesService roles, AuditStore audit, StoreFeeds storeFeeds,
                                        AuditCsv csv) {
        this.tickets = tickets;
        this.roles = roles;
        this.audit = audit;
        this.storeFeeds = storeFeeds;
        this.csv = csv;
    }

    @PostMapping("/ws-ticket")
    public ResponseEntity<WsTicketDto> ticket(HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        String tenant = requireStore(request);
        List<RoleDto> current = roles.fresh(user.getId(), tenant);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo verificar el acceso");
        }
        if (!RolesService.has(current, RolesService.ADMINISTRADOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        String ticket = tickets.issue(new WsTicket(user.getId(), tenant));
        return ResponseEntity.ok(new WsTicketDto(ticket, tickets.ttlSeconds()));
    }

    @GetMapping("/store/audit")
    public ResponseEntity<PageDto<AuditEvent>> history(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + AuditQueries.DEFAULT_SIZE) int size
    ) {
        String tenant = requireStore(request);
        return ResponseEntity.ok(audit.page(tenant, AuditQueries.query(from, to, userId, entity, null, action), page, size));
    }

    @GetMapping("/store/audit/export")
    public ResponseEntity<byte[]> export(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action
    ) {
        String tenant = requireStore(request);
        return AuditQueries.export(audit, csv, tenant, AuditQueries.query(from, to, userId, entity, null, action), tenant);
    }

    @GetMapping("/store/audit/{entity}/{entityId}")
    public ResponseEntity<PageDto<AuditEvent>> entityHistory(
            HttpServletRequest request,
            @PathVariable String entity,
            @PathVariable String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + AuditQueries.DEFAULT_SIZE) int size
    ) {
        String tenant = requireStore(request);
        return ResponseEntity.ok(audit.page(tenant, AuditQueries.query(null, null, null, entity, entityId, null), page, size));
    }

    @GetMapping("/store/users")
    public ResponseEntity<StoreUsersDto> users(HttpServletRequest request) {
        return ResponseEntity.ok(orUnavailable(storeFeeds.users(requireStore(request))));
    }

    @GetMapping("/store/usage")
    public ResponseEntity<StoreUsageDto> usage(HttpServletRequest request) {
        return ResponseEntity.ok(orUnavailable(storeFeeds.usage(requireStore(request))));
    }

    private static <T> T orUnavailable(T value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo leer el estado de la tienda");
        }
        return value;
    }

    private static String requireStore(HttpServletRequest request) {
        String tenant = HeaderAuthFilter.tenantOf(request);
        if (TenantConstants.PUBLIC.equals(tenant) || !AuditStore.validSchema(tenant)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return tenant;
    }
}
