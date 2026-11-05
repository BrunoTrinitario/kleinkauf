package com.kleinkauf.users.services;

import com.kleinkauf.common.dto.mail.OrderConfirmationMailDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.util.CacheUtil;
import com.kleinkauf.users.util.UserValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/*
Order confirmation email for an online order (sales asks for it after creating the order, docs/mail.md).
Sent once per order: if sales repeats the request (retry, timeout) the second one is ignored. If the email fails
the mark is released so a new request can send it.
 */
@Service
public class OrderMailService {

    private static final Duration SENT_MARK_TTL = Duration.ofDays(2);
    private static final Locale AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", AR);

    @Autowired
    private MailService mailService;

    @Autowired
    private StoreMailIdentity storeMailIdentity;

    @Autowired
    private CacheUtil cacheUtil;

    // Dates arrive in server time (UTC in Docker) and are shown in Argentine time
    @Value("${users.dashboard.zone:America/Argentina/Buenos_Aires}")
    private String zone;

    public boolean sendConfirmation(OrderConfirmationMailDto order) throws ApiException {
        if (order == null || order.number() == null || order.number().isBlank()) {
            throw new ApiException("The order number is required", HttpStatus.BAD_REQUEST);
        }
        UserValidator.validateEmail(order.buyerEmail());
        String tenant = TenantContext.getCurrentTenant();
        if (!cacheUtil.claimOrderMail(tenant, order.number(), SENT_MARK_TTL)) {
            return false;
        }
        StoreMailIdentity.Sender sender = storeMailIdentity.current();
        boolean sent = mailService.sendMail(sender, order.buyerEmail().trim(),
                subject(sender, order), content(sender, order));
        if (!sent) {
            cacheUtil.releaseOrderMail(tenant, order.number());
        }
        return sent;
    }

    static String subject(StoreMailIdentity.Sender sender, OrderConfirmationMailDto order) {
        return "Recibimos tu pedido " + order.number() + " en " + sender.name();
    }

    String content(StoreMailIdentity.Sender sender, OrderConfirmationMailDto order) {
        StringBuilder text = new StringBuilder();
        String name = order.buyerName() == null || order.buyerName().isBlank() ? "" : " " + order.buyerName().trim();
        text.append("¡Hola").append(name).append("!\n\n")
                .append("Recibimos tu pedido ").append(order.number()).append(" en ").append(sender.name())
                .append(".\n\n");

        text.append("Detalle:\n");
        if (order.items() != null) {
            for (OrderConfirmationMailDto.Item item : order.items()) {
                text.append("- ").append(item.quantity()).append(" x ").append(item.name())
                        .append(": ").append(money(item.subtotal())).append('\n');
            }
        }
        if (order.discount() != null && order.discount().signum() > 0) {
            text.append("Descuento: -").append(money(order.discount())).append('\n');
        }
        text.append("Total: ").append(money(order.total())).append("\n\n");

        if ("ENVIO".equals(order.deliveryType())) {
            text.append("Entrega: envío");
            if (order.deliveryAddress() != null && !order.deliveryAddress().isBlank()) {
                text.append(" a ").append(order.deliveryAddress());
            }
            text.append(". El costo del envío lo coordinás con la tienda.\n\n");
        } else {
            text.append("Entrega: retiro en la tienda. Te avisan cuando esté listo.\n\n");
        }

        OrderConfirmationMailDto.Payment payment = order.payment();
        if (payment != null) {
            text.append("Para pagar, hacé una transferencia por el total:\n");
            line(text, "Alias", payment.alias());
            line(text, "CBU/CVU", payment.cbu());
            line(text, "Titular", payment.holder());
            line(text, "Banco", payment.bank());
            text.append('\n');
        }
        if (order.expiresAt() != null) {
            text.append("Si no se registra el pago antes del ").append(localTime(order.expiresAt()))
                    .append(", el pedido se cancela solo.\n\n");
        }

        text.append("Podés ver el estado del pedido y subir el comprobante de pago acá:\n")
                .append(sender.url()).append("/pedido/").append(order.number()).append("\n\n")
                .append("Si tenés alguna duda, respondé este mail y le llega a ").append(sender.name()).append(".\n");
        return text.toString();
    }

    private String localTime(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of(zone)).format(DATE_TIME) + " hs";
    }

    private static void line(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append("  ").append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(AR);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }
}
