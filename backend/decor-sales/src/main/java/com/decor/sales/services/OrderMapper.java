package com.decor.sales.services;

import com.decor.sales.dto.AddressDto;
import com.decor.sales.dto.AdminOrderDto;
import com.decor.sales.dto.AdminOrderItemDto;
import com.decor.sales.dto.BuyerDto;
import com.decor.sales.dto.OrderDto;
import com.decor.sales.dto.OrderItemDto;
import com.decor.sales.dto.OrderSummaryDto;
import com.decor.sales.dto.PaymentDetailsDto;
import com.decor.sales.dto.StatusChangeDto;
import com.decor.sales.entities.Address;
import com.decor.sales.entities.Order;
import com.decor.sales.entities.OrderItem;
import com.decor.sales.entities.OrderStatus;
import com.decor.sales.entities.SalesSettings;

import java.util.List;

// Entidades -> DTOs. Leer dentro de una transaccion: items e historial son lazy
public class OrderMapper {

    // Vista del comprador: sin costos ni usuarios del historial; datos de pago solo si falta pagar
    public static OrderDto toBuyerDto(Order order, SalesSettings settings) {
        boolean awaitingPayment = order.getStatus() == OrderStatus.PENDIENTE_PAGO
                || order.getStatus() == OrderStatus.RECHAZADO;
        PaymentDetailsDto payment = awaitingPayment ? SalesSettingsService.paymentDetails(settings) : null;
        List<OrderItemDto> items = order.getItems().stream()
                .map(i -> new OrderItemDto(i.getType(), i.getItemId(), i.getName(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();
        List<StatusChangeDto> history = order.getHistory().stream()
                .map(h -> new StatusChangeDto(h.getStatus(), h.getCreatedAt(), h.getReason(), null))
                .toList();
        return new OrderDto(order.getNumber(), order.getStatus(), order.getCreatedAt(), order.getExpiresAt(),
                buyer(order), order.getDeliveryType(), address(order.getAddress()), order.getNotes(),
                items, order.getDiscount(), order.getTotal(), payment, history);
    }

    public static AdminOrderDto toAdminDto(Order order) {
        List<AdminOrderItemDto> items = order.getItems().stream()
                .map(i -> new AdminOrderItemDto(i.getType(), i.getItemId(), i.getName(), i.getQuantity(),
                        i.getListPrice(), i.getUnitPrice(), i.getUnitCost(), i.getSubtotal()))
                .toList();
        List<StatusChangeDto> history = order.getHistory().stream()
                .map(h -> new StatusChangeDto(h.getStatus(), h.getCreatedAt(), h.getReason(), h.getUserId()))
                .toList();
        return new AdminOrderDto(order.getNumber(), order.getStatus(), order.getChannel(), order.getPaymentMethod(),
                buyer(order), order.getUserId(), order.getDeliveryType(), address(order.getAddress()), order.getNotes(),
                items, order.getDiscount(), order.getTotal(), order.isStockReview(), order.getExpiresAt(),
                order.getCreatedAt(), order.getUpdatedAt(), history);
    }

    public static OrderSummaryDto toSummary(Order order) {
        int units = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        return new OrderSummaryDto(order.getNumber(), order.getStatus(), order.getChannel(), order.getPaymentMethod(),
                order.getBuyerName(), order.getBuyerEmail(), units, order.getTotal(), order.isStockReview(),
                order.getCreatedAt());
    }

    private static BuyerDto buyer(Order order) {
        return new BuyerDto(order.getBuyerName(), order.getBuyerEmail(), order.getBuyerPhone());
    }

    private static AddressDto address(Address a) {
        if (a == null || a.getStreet() == null) return null;
        return new AddressDto(a.getStreet(), a.getNumber(), a.getApartment(), a.getCity(), a.getProvince(), a.getPostalCode());
    }
}
