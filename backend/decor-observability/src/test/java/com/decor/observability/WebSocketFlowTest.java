package com.decor.observability;

import com.decor.common.constants.Headers;
import com.decor.common.dto.RoleDto;
import com.decor.observability.audit.AuditStore;
import com.decor.observability.dto.PageDto;
import com.decor.observability.dto.StoreUsersDto;
import com.decor.observability.security.UsersClient;
import com.decor.observability.store.StoreFeeds;
import com.decor.observability.ws.AccessRevalidator;
import com.decor.observability.ws.Channels;
import com.decor.observability.ws.SessionRegistry;
import com.decor.observability.ws.TicketStore;
import com.decor.observability.ws.WsTicket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
El servicio levantado de verdad con un cliente WebSocket. Redis (tickets) y users (roles) son mocks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "observability.scheduling.enabled=false",
        "observability.events.enabled=false",
        "observability.audit.enabled=false"
})
class WebSocketFlowTest {

    private static final WsTicket SUPERADMIN = new WsTicket(1L, "public");
    private static final List<RoleDto> SUPERADMIN_ROLES = List.of(new RoleDto("SUPERADMIN", 0));

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    SessionRegistry sessions;

    @Autowired
    AccessRevalidator revalidator;

    @Autowired
    ObjectMapper mapper;

    @MockitoBean
    TicketStore tickets;

    @MockitoBean
    UsersClient usersClient;

    @MockitoBean
    AuditStore auditStore;

    @MockitoBean
    StoreFeeds storeFeeds;

    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        when(tickets.redeem(anyString())).thenReturn(Optional.empty());
        when(tickets.redeem("bueno")).thenReturn(Optional.of(SUPERADMIN));
        when(tickets.ttlSeconds()).thenReturn(30L);
        when(usersClient.getUserRoles(eq(1L), any())).thenReturn(SUPERADMIN_ROLES);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (session != null && session.isOpen()) {
            session.close();
        }
    }

    @Test
    void conTicketSeSuscribeYRecibeLosEventos() throws Exception {
        connect("bueno", null);

        send(Map.of("type", "subscribe", "channel", Channels.PLATFORM_HEALTH));
        assertThat(next().path("type").asText()).isEqualTo("subscribed");

        sessions.publish(Channels.PLATFORM_HEALTH, Map.of("estado", "UP"));
        JsonNode event = next();
        assertThat(event.path("type").asText()).isEqualTo("event");
        assertThat(event.path("channel").asText()).isEqualTo(Channels.PLATFORM_HEALTH);
        assertThat(event.path("data").path("estado").asText()).isEqualTo("UP");
        assertThat(event.path("at").asText()).endsWith("Z");

        // Lo que no esta suscripto no llega
        sessions.publish(Channels.PLATFORM_TRAFFIC, Map.of("x", 1));
        send(Map.of("type", "ping"));
        assertThat(next().path("type").asText()).isEqualTo("pong");
    }

    @Test
    void elSuperadminNoPuedeSuscribirseACanalesDeTienda() throws Exception {
        connect("bueno", null);

        send(Map.of("type", "subscribe", "channel", "store.activity"));

        JsonNode reply = next();
        assertThat(reply.path("type").asText()).isEqualTo("error");
        assertThat(session.isOpen()).isTrue();
    }

    @Test
    void sinTicketValidoNoSeAbre() {
        assertThatThrownBy(() -> connect("vencido", null)).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect(null, null)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void unTicketDePlataformaNoSirveDesdeUnaTienda() {
        assertThatThrownBy(() -> connect("bueno", "mi_tienda")).isInstanceOf(ExecutionException.class);
    }

    @Test
    void siLeQuitaronElRolSeCortaElWs() throws Exception {
        connect("bueno", null);
        when(usersClient.getUserRoles(eq(1L), any())).thenReturn(List.of());

        revalidator.revalidate();

        CloseStatus status = closed.get(5, TimeUnit.SECONDS);
        assertThat(status.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
    }

    @Test
    void siUsersNoRespondeNoSeCortaANadie() throws Exception {
        connect("bueno", null);
        when(usersClient.getUserRoles(eq(1L), any())).thenReturn(null);

        revalidator.revalidate();

        send(Map.of("type", "ping"));
        assertThat(next().path("type").asText()).isEqualTo("pong");
    }

    @Test
    void elTicketSeEmiteSoloAlSuperadminDeLaPlataforma() {
        when(usersClient.getUserRoles(eq(4L), any())).thenReturn(SUPERADMIN_ROLES);
        when(tickets.issue(new WsTicket(4L, "public"))).thenReturn("nuevo");

        ResponseEntity<Map> ok = postTicket("4", null);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).containsEntry("ticket", "nuevo").containsEntry("expiresIn", 30);

        // Sin usuario, sin el rol o desde una tienda
        assertThat(postTicket(null, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        when(usersClient.getUserRoles(eq(2L), any())).thenReturn(List.of(new RoleDto("ADMINISTRADOR", 0)));
        assertThat(postTicket("2", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postTicket("4", "mi_tienda").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void elResumenDeLaPlataformaEsSoloParaElSuperadmin() {
        // Otro usuario: los roles quedan 1 minuto en cache y otro test le saca el rol al usuario 1
        when(usersClient.getUserRoles(eq(3L), any())).thenReturn(SUPERADMIN_ROLES);
        HttpHeaders headers = new HttpHeaders();
        headers.set(Headers.USER_ID, "3");
        ResponseEntity<Map> ok = rest.exchange("/api/v1/platform/observability/overview", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).containsKeys("services", "traffic");
        assertThat(rest.getForEntity("/api/v1/platform/observability/overview", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void elAdministradorAbreElWsDeSuTiendaYVeSoloSuAuditoria() throws Exception {
        when(tickets.redeem("tienda")).thenReturn(Optional.of(new WsTicket(10L, "mi_tienda")));
        connect("tienda", "mi_tienda");

        send(Map.of("type", "subscribe", "channel", Channels.STORE_AUDIT));
        assertThat(next().path("type").asText()).isEqualTo("subscribed");
        // Los canales de la plataforma no son para la tienda
        send(Map.of("type", "subscribe", "channel", Channels.PLATFORM_HEALTH));
        assertThat(next().path("type").asText()).isEqualTo("error");

        sessions.publishToTenant(Channels.STORE_AUDIT, "otra_tienda", Map.of("entity", "ajena"));
        sessions.publishToTenant(Channels.STORE_AUDIT, "mi_tienda", Map.of("entity", "Product"));
        JsonNode event = next();
        assertThat(event.path("data").path("entity").asText()).isEqualTo("Product");
    }

    @Test
    void elAdministradorSeSuscribeALaActividadElUsoYLosConectadosDeSuTienda() throws Exception {
        when(tickets.redeem("tienda")).thenReturn(Optional.of(new WsTicket(10L, "mi_tienda")));
        connect("tienda", "mi_tienda");

        for (String channel : List.of(Channels.STORE_ACTIVITY, Channels.STORE_USAGE, Channels.STORE_USERS)) {
            send(Map.of("type", "subscribe", "channel", channel));
            assertThat(next().path("type").asText()).isEqualTo("subscribed");
        }
        assertThat(sessions.tenantsSubscribedTo(Channels.STORE_USERS)).containsExactly("mi_tienda");

        sessions.publishToTenant(Channels.STORE_ACTIVITY, "otra_tienda", Map.of("type", "order.created"));
        sessions.publishToTenant(Channels.STORE_ACTIVITY, "mi_tienda", Map.of("type", "stock.low"));
        JsonNode event = next();
        assertThat(event.path("channel").asText()).isEqualTo(Channels.STORE_ACTIVITY);
        assertThat(event.path("data").path("type").asText()).isEqualTo("stock.low");
    }

    @Test
    void elAdministradorPideLosConectadosYElUsoDeSuTienda() {
        when(usersClient.getUserRoles(eq(12L), any())).thenReturn(List.of(new RoleDto("ADMINISTRADOR", 0)));
        when(usersClient.getUserRoles(eq(13L), any())).thenReturn(List.of(new RoleDto("EMPLEADO", 0)));
        when(storeFeeds.users("mi_tienda")).thenReturn(new StoreUsersDto(0, List.of()));
        when(storeFeeds.usage("mi_tienda")).thenReturn(null);

        assertThat(get("/api/v1/observability/store/users", "12", "mi_tienda").getStatusCode()).isEqualTo(HttpStatus.OK);
        // Redis caido
        assertThat(get("/api/v1/observability/store/usage", "12", "mi_tienda").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(get("/api/v1/observability/store/users", "13", "mi_tienda").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Sin tienda (desde la plataforma) no hay panel de tienda
        assertThat(get("/api/v1/observability/store/users", "12", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unTicketDeTiendaNoSirveDesdeOtraTiendaNiDesdeLaPlataforma() {
        when(tickets.redeem("tienda")).thenReturn(Optional.of(new WsTicket(10L, "mi_tienda")));
        assertThatThrownBy(() -> connect("tienda", "otra_tienda")).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect("tienda", null)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void elTicketDeLaTiendaEsSoloParaSuAdministrador() {
        when(usersClient.getUserRoles(eq(10L), any())).thenReturn(List.of(new RoleDto("ADMINISTRADOR", 0)));
        when(usersClient.getUserRoles(eq(11L), any())).thenReturn(List.of(new RoleDto("EMPLEADO", 0)));
        when(tickets.issue(new WsTicket(10L, "mi_tienda"))).thenReturn("de-tienda");

        ResponseEntity<Map> ok = post("/api/v1/observability/ws-ticket", "10", "mi_tienda");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).containsEntry("ticket", "de-tienda");

        assertThat(post("/api/v1/observability/ws-ticket", "11", "mi_tienda").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Sin tienda (desde la plataforma) no hay panel de tienda
        assertThat(post("/api/v1/observability/ws-ticket", "10", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cadaAdministradorConsultaLaAuditoriaDeSuTiendaYElSuperadminLaDeLaPlataforma() {
        when(usersClient.getUserRoles(eq(12L), any())).thenReturn(List.of(new RoleDto("ADMINISTRADOR", 0)));
        when(usersClient.getUserRoles(eq(13L), any())).thenReturn(SUPERADMIN_ROLES);
        when(auditStore.page(any(), any(), anyInt(), anyInt())).thenReturn(new PageDto<>(List.of(), 0, 20, 0, 0));

        assertThat(get("/api/v1/observability/store/audit?entity=Product&action=update", "12", "mi_tienda").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        verify(auditStore).page(eq("mi_tienda"), eq(new AuditStore.Query(null, null, null, "Product", null, "UPDATE")), eq(0), eq(20));

        assertThat(get("/api/v1/observability/store/audit/Product/12", "12", "mi_tienda").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditStore).page(eq("mi_tienda"), eq(new AuditStore.Query(null, null, null, "Product", "12", null)), eq(0), eq(20));

        assertThat(get("/api/v1/platform/observability/audit", "13", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditStore).page(eq("public"), any(), eq(0), eq(20));

        // El superadmin no ve la de las tiendas, ni el administrador la de la plataforma
        assertThat(get("/api/v1/observability/store/audit", "13", "mi_tienda").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/platform/observability/audit", "12", "mi_tienda").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/observability/store/audit?from=ayer", "12", "mi_tienda").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map> post(String path, String userId, String tenant) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(headers(userId, tenant)), Map.class);
    }

    private ResponseEntity<Map> get(String path, String userId, String tenant) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(userId, tenant)), Map.class);
    }

    private static HttpHeaders headers(String userId, String tenant) {
        HttpHeaders headers = new HttpHeaders();
        if (userId != null) headers.set(Headers.USER_ID, userId);
        if (tenant != null) headers.set(Headers.FORWARDED_HOST, tenant);
        return headers;
    }

    private ResponseEntity<Map> postTicket(String userId, String tenant) {
        HttpHeaders headers = new HttpHeaders();
        if (userId != null) headers.set(Headers.USER_ID, userId);
        if (tenant != null) headers.set(Headers.FORWARDED_HOST, tenant);
        return rest.exchange("/api/v1/platform/observability/ws-ticket", HttpMethod.POST,
                new HttpEntity<>(headers), Map.class);
    }

    private void connect(String ticket, String tenant) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (tenant != null) headers.set(Headers.FORWARDED_HOST, tenant);
        String query = ticket == null ? "" : "?ticket=" + ticket;
        session = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                received.add(message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                closed.complete(status);
            }
        }, headers, URI.create("ws://localhost:" + port + "/ws" + query)).get(5, TimeUnit.SECONDS);
    }

    private void send(Map<String, Object> message) throws Exception {
        session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
    }

    private JsonNode next() throws Exception {
        String message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("mensaje del servidor").isNotNull();
        return mapper.readTree(message);
    }
}
