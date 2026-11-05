package com.kleinkauf.sales.services;

import com.kleinkauf.sales.dto.AddressDto;
import com.kleinkauf.sales.dto.AdminOrderDto;
import com.kleinkauf.sales.dto.AdminOrderItemDto;
import com.kleinkauf.sales.dto.BuyerDto;
import com.kleinkauf.sales.dto.OrderDto;
import com.kleinkauf.sales.dto.OrderItemDto;
import com.kleinkauf.sales.dto.OrderSummaryDto;
import com.kleinkauf.sales.dto.PaymentDetailsDto;
import com.kleinkauf.sales.dto.ReceiptDto;
import com.kleinkauf.sales.dto.StatusChangeDto;
import com.kleinkauf.sales.entities.Address;
import com.kleinkauf.sales.entities.Order;
import com.kleinkauf.sales.entities.OrderItem;
import com.kleinkauf.sales.entities.SalesSettings;

import java.util.List;

// Entities -> DTOs. Read inside a transaction: items and history are lazy
public class OrderMapper {

    // Buyer view: no costs or history users; payment details only if payment is pending
    public static OrderDto toBuyerDto(Order order, SalesSettings settings) {
        PaymentDetailsDto payment = order.getStatus().acceptsReceipt() ? SalesSettingsService.paymentDetails(settings) : null;
        List<OrderItemDto> items = order.getItems().stream()
                .map(i -> new OrderItemDto(i.getType(), i.getItemId(), i.getName(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();
        List<StatusChangeDto> history = order.getHistory().stream()
                .map(h -> new StatusChangeDto(h.getStatus(), h.getCreatedAt(), h.getReason(), null))
                .toList();
        return new OrderDto(order.getNumber(), order.getStatus(), order.getCreatedAt(), order.getExpiresAt(),
                buyer(order), order.getDeliveryType(), address(order.getAddress()), order.getNotes(),
                items, order.getDiscount(), order.getTotal(), payment, history, order.getReceipts().size());
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
                order.getCreatedAt(), order.getUpdatedAt(), history,
                order.getReceipts().stream()
                        .map(r -> new ReceiptDto(r.getId(), r.getContentType(), r.getSize(), r.getCreatedAt()))
                        .toList());
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
