package com.kleinkauf.sales.services;

import com.kleinkauf.common.dto.mail.OrderConfirmationMailDto;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.sales.dto.PaymentDetailsDto;
import com.kleinkauf.sales.entities.Address;
import com.kleinkauf.sales.entities.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/*
Order confirmation email for the buyer of an online order (docs/mail.md). users sends it (it has the mail server and
knows the store's name and owner); here the order is summarized and the request goes out in the background so the
checkout does not wait for the mail server. If users does not answer, it stays in the log and the order is unaffected.
 */
@Slf4j
@Component
public class OrderMail {

    @Autowired
    private UsersClient usersClient;

    @Autowired
    @Qualifier("applicationTaskExecutor")
    private TaskExecutor executor;

    public void confirmation(Order order, PaymentDetailsDto payment) {
        String tenant = TenantContext.getCurrentTenant();
        if (tenant == null || order.getBuyerEmail() == null || order.getBuyerEmail().isBlank()) {
            return;
        }
        OrderConfirmationMailDto mail = toMail(order, payment);
        executor.execute(() -> {
            try {
                usersClient.sendOrderConfirmation(mail, tenant);
            } catch (RuntimeException e) {
                log.warn("No se pudo pedir el mail de confirmación del pedido {} ({}): {}",
                        order.getNumber(), tenant, e.toString());
            }
        });
    }

    static OrderConfirmationMailDto toMail(Order order, PaymentDetailsDto payment) {
        List<OrderConfirmationMailDto.Item> items = order.getItems().stream()
                .map(i -> new OrderConfirmationMailDto.Item(i.getName(), i.getQuantity(), i.getSubtotal()))
                .toList();
        return new OrderConfirmationMailDto(
                order.getNumber(),
                order.getBuyerName(),
                order.getBuyerEmail(),
                items,
                order.getDiscount(),
                order.getTotal(),
                order.getDeliveryType() == null ? null : order.getDeliveryType().name(),
                address(order.getAddress()),
                order.getExpiresAt(),
                payment == null ? null
                        : new OrderConfirmationMailDto.Payment(payment.alias(), payment.cbu(), payment.holder(), payment.bank())
        );
    }

    // "Av. Siempreviva 742, 3° B, Springfield, Buenos Aires (1234)"
    static String address(Address a) {
        if (a == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        String street = join(a.getStreet(), a.getNumber());
        if (!street.isEmpty()) parts.add(street);
        if (notBlank(a.getApartment())) parts.add(a.getApartment().trim());
        if (notBlank(a.getCity())) parts.add(a.getCity().trim());
        if (notBlank(a.getProvince())) parts.add(a.getProvince().trim());
        String text = String.join(", ", parts);
        if (notBlank(a.getPostalCode())) text += " (" + a.getPostalCode().trim() + ")";
        return text.isBlank() ? null : text.trim();
    }

    private static String join(String street, String number) {
        return String.join(" ", Stream.of(street, number)
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
