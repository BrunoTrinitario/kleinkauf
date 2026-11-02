package com.decor.sales.services;

import com.decor.common.dto.TenantDto;
import com.decor.common.servlet.tenant.TenantContext;
import com.decor.sales.entities.OrderStatus;
import com.decor.sales.repositories.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/*
Vence los pedidos que no subieron el comprobante a tiempo (PENDIENTE_PAGO y RECHAZADO):
pasan a VENCIDO y se devuelve el stock. Corre cada sales.expiration.interval (1 hora) y recorre
todas las tiendas. Un pedido que falla (products caido) se reintenta en la pasada siguiente.
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

    // Devuelve cuantos pedidos vencio
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
