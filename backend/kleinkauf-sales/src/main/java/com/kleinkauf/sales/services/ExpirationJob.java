package com.kleinkauf.sales.services;

import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.sales.entities.OrderStatus;
import com.kleinkauf.sales.repositories.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/*
Expires the orders that did not upload the receipt in time (PENDIENTE_PAGO and RECHAZADO):
they move to VENCIDO and the stock is returned. Runs every sales.expiration.interval (1 hour) and goes through
every store. An order that fails (products down) is retried on the next pass.
 */
@Slf4j
@Component
public class ExpirationJob {

    @Autowired
    private UserService userService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStatusService statusService;

    @Scheduled(fixedDelayString = "${sales.expiration.interval:PT1H}", initialDelayString = "${sales.expiration.initial-delay:PT1M}")
    public void run() {
        List<TenantDto> tenants = userService.getTenants();
        if (tenants == null) return;
        for (TenantDto tenant : tenants) {
            if (tenant.deletedAt() != null) continue;
            TenantContext.setCurrentTenant(tenant.schema());
            try {
                expireTenant(tenant.schema(), LocalDateTime.now());
            } catch (RuntimeException e) {
                log.error("No se pudieron vencer los pedidos de {}", tenant.schema(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    // Returns how many orders it expired
    int expireTenant(String tenant, LocalDateTime now) {
        int expired = 0;
        List<String> numbers = orderRepository.findExpiredNumbers(EnumSet.of(OrderStatus.PENDIENTE_PAGO, OrderStatus.RECHAZADO), now);
        for (String number : numbers) {
            try {
                if (statusService.expire(number, now)) expired++;
            } catch (Exception e) {
                log.warn("No se pudo vencer el pedido {} de {}: {}", number, tenant, e.toString());
            }
        }
        if (expired > 0) log.info("Vencieron {} pedidos de {}", expired, tenant);
        return expired;
    }
}
