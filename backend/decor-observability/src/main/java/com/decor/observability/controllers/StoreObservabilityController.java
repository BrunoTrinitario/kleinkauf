package com.decor.observability.controllers;

import com.decor.common.audit.AuditEvent;
import com.decor.common.constants.TenantConstants;
import com.decor.common.dto.RoleDto;
import com.decor.common.servlet.security.CustomUserDetails;
import com.decor.observability.audit.AuditStore;
import com.decor.observability.dto.PageDto;
import com.decor.observability.dto.StoreUsageDto;
import com.decor.observability.dto.StoreUsersDto;
import com.decor.observability.dto.WsTicketDto;
import com.decor.observability.security.HeaderAuthFilter;
import com.decor.observability.security.RolesService;
import com.decor.observability.store.StoreFeeds;
import com.decor.observability.ws.TicketStore;
import com.decor.observability.ws.WsTicket;
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
Panel de la tienda (solo ADMINISTRADOR, ver SecurityConfig). Siempre de la tienda de la request: sin tienda
(desde la plataforma) responde 404. El superadmin no ve la auditoria de las tiendas.
- POST /ws-ticket: ticket para el WS de la tienda, con los roles actuales.
- GET /store/audit: historial de la tienda, paginado y con filtros (fecha, usuario, entidad, accion).
- GET /store/audit/{entity}/{entityId}: historial de una cosa puntual (ej. Product/12).
- GET /store/users y /store/usage: lo mismo que mandan store.users y store.usage, para cuando se abre el panel
  (el WS manda solo lo nuevo). 503 si no se pudo leer Redis.
 */
@RestController
@RequestMapping("/api/v1/observability")
public class StoreObservabilityController {

    private final TicketStore tickets;
    private final RolesService roles;
    private final AuditStore audit;
    private final StoreFeeds storeFeeds;

    public StoreObservabilityController(TicketStore tickets, RolesService roles, AuditStore audit, StoreFeeds storeFeeds) {
        this.tickets = tickets;
        this.roles = roles;
        this.audit = audit;
        this.storeFeeds = storeFeeds;
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
