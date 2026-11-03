package com.kleinkauf.observability.ws;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.observability.security.RolesService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Acceso revocado (docs/observabilidad.md): cada observability.ws.revalidate-interval (1 minuto) se piden de
nuevo los roles de los conectados y se corta el WS de quien ya no tiene el rol (superadmin desactivado).
Es el mismo minuto en que se le corta el REST. Si users no responde nadie se desconecta: no se echa a todos
por una caida. El frontend, al cortarse, pide otro ticket y ya no se lo dan.
 */
@Component
public class AccessRevalidator {

    // 1008: violacion de politica (acceso revocado)
    static final CloseStatus REVOKED = CloseStatus.POLICY_VIOLATION.withReason("Acceso revocado");

    private final SessionRegistry sessions;
    private final RolesService roles;

    public AccessRevalidator(SessionRegistry sessions, RolesService roles) {
        this.sessions = sessions;
        this.roles = roles;
    }

    public void revalidate() {
        // Un solo pedido por usuario aunque tenga varias pestanas abiertas. HashMap admite el null de "no se sabe"
        Map<WsTicket, Boolean> allowed = new HashMap<>();
        for (SessionRegistry.Client client : sessions.clients()) {
            if (!allowed.containsKey(client.ticket())) {
                allowed.put(client.ticket(), stillAllowed(client.ticket()));
            }
            if (Boolean.FALSE.equals(allowed.get(client.ticket()))) {
                sessions.close(client, REVOKED);
            }
        }
    }

    // null: no se sabe (users no respondio)
    private Boolean stillAllowed(WsTicket ticket) {
        List<RoleDto> current = roles.fresh(ticket.userId(), ticket.tenant());
        if (current == null) {
            return null;
        }
        return RolesService.has(current, RolesService.requiredRole(ticket));
    }
}
