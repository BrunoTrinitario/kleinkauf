package com.decor.observability.controllers;

import com.decor.common.constants.TenantConstants;
import com.decor.common.dto.RoleDto;
import com.decor.common.servlet.security.CustomUserDetails;
import com.decor.observability.collector.PlatformState;
import com.decor.observability.dto.PlatformOverviewDto;
import com.decor.observability.dto.WsTicketDto;
import com.decor.observability.security.HeaderAuthFilter;
import com.decor.observability.security.RolesService;
import com.decor.observability.ws.TicketStore;
import com.decor.observability.ws.WsTicket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/*
Panel de la plataforma (solo SUPERADMIN, ver SecurityConfig). Sin tienda: desde una tienda responde 404,
igual que el resto de /api/v1/platform/**.
- POST /ws-ticket: ticket para abrir el WS. Se emite con los roles actuales (pedidos a users en el momento):
  un superadmin desactivado no consigue otro.
- GET /overview: la ultima lectura del recolector, para mostrar algo antes del primer mensaje del WS.
 */
@RestController
@RequestMapping("/api/v1/platform/observability")
public class PlatformObservabilityController {

    private final TicketStore tickets;
    private final RolesService roles;
    private final PlatformState state;

    public PlatformObservabilityController(TicketStore tickets, RolesService roles, PlatformState state) {
        this.tickets = tickets;
        this.roles = roles;
        this.state = state;
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

    private static void requirePlatform(HttpServletRequest request) {
        if (!TenantConstants.PUBLIC.equals(HeaderAuthFilter.tenantOf(request))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
