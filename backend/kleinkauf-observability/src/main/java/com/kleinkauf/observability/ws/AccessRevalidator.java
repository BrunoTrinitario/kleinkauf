package com.kleinkauf.observability.ws;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.observability.security.RolesService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Revoked access (docs/features/observability.md): every observability.ws.revalidate-interval (1 minute) the roles of the
online users are requested again and the WS of whoever no longer has the role (deactivated superadmin) is closed.
It is the same minute in which their REST access is cut. If users does not answer nobody is disconnected: we do not kick everyone
out because of an outage. The frontend, when cut off, asks for another ticket and no longer gets one.
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
        // A single request per user even with several tabs open. HashMap accepts the "unknown" null
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

    // null: unknown (users did not answer)
    private Boolean stillAllowed(WsTicket ticket) {
        List<RoleDto> current = roles.fresh(ticket.userId(), ticket.tenant());
        if (current == null) {
            return null;
        }
        return RolesService.has(current, RolesService.requiredRole(ticket));
    }
}
