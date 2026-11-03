package com.decor.sales.services;

import com.decor.common.audit.AuditLog;
import com.decor.common.dto.stock.ReservationDto;
import com.decor.common.dto.stock.ReservationItemDto;
import com.decor.common.dto.stock.ReservedItemDto;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.sales.constants.ErrorMessages;
import com.decor.sales.dto.AdminOrderDto;
import com.decor.sales.dto.BuyerDto;
import com.decor.sales.dto.LocalOrderDto;
import com.decor.sales.dto.LocalOrderItemDto;
import com.decor.sales.dto.OrderSummaryDto;
import com.decor.sales.dto.PageDto;
import com.decor.sales.dto.StatusUpdateDto;
import com.decor.sales.entities.DeliveryType;
import com.decor.sales.entities.Order;
import com.decor.sales.entities.OrderItem;
import com.decor.sales.entities.OrderStatus;
import com.decor.sales.entities.PaymentMethod;
import com.decor.sales.entities.PaymentReceipt;
import com.decor.sales.entities.SalesChannel;
import com.decor.sales.repositories.OrderRepository;
import com.decor.sales.repositories.PaymentReceiptRepository;
import com.decor.sales.util.ReceiptStorage;
import com.decor.sales.util.SalesTime;
import com.decor.sales.util.SecurityUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
Pedidos en el panel del administrador: lista con filtros, detalle, comprobantes, cambios de estado
y ventas del local.
Venta del local (ya cobrada):
1. Valida y reserva el stock en products como venta del local: acepta items sin precio y, si falta
   stock, products lo ajusta y el pedido queda marcado para revisar (stockReview).
2. Calcula los precios (el del administrador o el actual) y el descuento. Si falta algun precio o el
   descuento supera el subtotal, devuelve el stock.
3. Suma a vendidos y guarda. Si algo falla, vuelve atras lo hecho en products.
 */
@Slf4j
@Service
public class AdminOrderService {

    public static final String LOCAL_SALE_REASON = "Venta en el local";

    @Value("${sales.local.max-lines:100}")
    private int maxLines;

    @Value("${sales.local.max-quantity:999}")
    private int maxQuantity;

    // Registro a mano de lo que no es un cambio de una entidad @Audited (docs/auditoria.md)
    @Autowired
    private AuditLog auditLog;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentReceiptRepository receiptRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderStatusService statusService;

    @Autowired
    private OrderStock orderStock;

    @Autowired
    private ReceiptStorage receiptStorage;

    @Autowired
    private SalesTime salesTime;

    @Autowired
    private OrderEvents orderEvents;

    // Comprobante a descargar: el registro y el archivo
    public record ReceiptFile(PaymentReceipt receipt, Resource file) {
    }

    // Filtros de la lista; todos opcionales. from y to son fechas (yyyy-MM-dd) inclusive
    public record Filter(String status, String channel, String paymentMethod, Boolean stockReview,
                         String from, String to, String q) {
    }

    @Transactional(readOnly = true)
    public PageDto<OrderSummaryDto> list(Filter filter, Integer page, Integer size) throws ApiException {
        PageRequest pageRequest = OrderService.pageRequest(page, size)
                .withSort(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return OrderService.toPage(orderRepository.findAll(specification(filter, salesTime), pageRequest));
    }

    @Transactional(readOnly = true)
    public AdminOrderDto get(String number) throws ApiException {
        return OrderMapper.toAdminDto(find(number));
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminOrderDto changeStatus(String number, StatusUpdateDto dto) throws ApiException {
        Order order = find(number);
        return OrderMapper.toAdminDto(statusService.change(order, dto == null ? null : dto.status(),
                SecurityUtil.currentUserId(), dto == null ? null : dto.reason()));
    }

    @Transactional(readOnly = true)
    public ReceiptFile receipt(String number, Long id) throws ApiException {
        PaymentReceipt receipt = receiptRepository.findByIdAndOrder_Number(id, number.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(ErrorMessages.RECEIPT_NOT_FOUND, HttpStatus.NOT_FOUND));
        Resource file = receiptStorage.load(receipt.getPath());
        if (!file.exists()) {
            log.error("Falta el archivo del comprobante {} del pedido {}: {}", id, number, receipt.getPath());
            throw new ApiException(ErrorMessages.RECEIPT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        // Lectura sensible: el comprobante de pago
        auditLog.read("PaymentReceipt", id);
        return new ReceiptFile(receipt, file);
    }

    public AdminOrderDto createLocal(LocalOrderDto dto) throws ApiException {
        List<LocalOrderItemDto> items = validate(dto);
        String number = orderService.newNumber();
        List<ReservationItemDto> lines = items.stream()
                .map(i -> new ReservationItemDto(i.type(), i.id(), i.quantity()))
                .toList();

        ReservationDto reservation = orderStock.reserve(number, lines, true);
        Order order;
        try {
            order = buildLocalOrder(number, dto, items, reservation);
        } catch (ApiException e) {
            orderStock.releaseQuietly(number);
            throw e;
        }

        try {
            orderStock.markSold(number, lines);
        } catch (ApiException e) {
            orderStock.releaseQuietly(number);
            throw e;
        }

        try {
            order = orderRepository.save(order);
        } catch (RuntimeException e) {
            log.error("No se pudo guardar la venta del local {}: se vuelve atras en products", number, e);
            orderStock.unmarkSoldQuietly(number, lines);
            orderStock.releaseQuietly(number);
            throw new ApiException(ErrorMessages.ORDER_NOT_CREATED, HttpStatus.SERVICE_UNAVAILABLE);
        }
        orderEvents.created(order);
        return OrderMapper.toAdminDto(order);
    }

    private Order find(String number) throws ApiException {
        return orderRepository.findByNumber(number.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(ErrorMessages.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    // Valida la venta del local y junta las lineas repetidas con el mismo precio
    private List<LocalOrderItemDto> validate(LocalOrderDto dto) throws ApiException {
        String invalidItems = ErrorMessages.INVALID_LOCAL_ITEMS.formatted(maxLines, maxQuantity);
        if (dto == null || dto.items() == null || dto.items().isEmpty()) {
            throw new ApiException(invalidItems, HttpStatus.BAD_REQUEST);
        }
        Map<String, LocalOrderItemDto> merged = new LinkedHashMap<>();
        for (LocalOrderItemDto item : dto.items()) {
            if (item == null || item.type() == null || item.id() == null || item.quantity() == null
                    || item.quantity() < 1 || (item.price() != null && item.price().signum() < 0)) {
                throw new ApiException(invalidItems, HttpStatus.BAD_REQUEST);
            }
            String key = item.type() + ":" + item.id() + ":" + (item.price() == null ? "" : item.price().stripTrailingZeros());
            merged.merge(key, item, (a, b) -> new LocalOrderItemDto(a.type(), a.id(), a.quantity() + b.quantity(), a.price()));
        }
        if (merged.size() > maxLines || merged.values().stream().anyMatch(i -> i.quantity() > maxQuantity)) {
            throw new ApiException(invalidItems, HttpStatus.BAD_REQUEST);
        }
        if (dto.paymentMethod() == null) {
            throw new ApiException(ErrorMessages.INVALID_PAYMENT_METHOD, HttpStatus.BAD_REQUEST);
        }
        if (dto.status() != OrderStatus.PAGADO && dto.status() != OrderStatus.ENTREGADO) {
            throw new ApiException(ErrorMessages.INVALID_LOCAL_STATUS, HttpStatus.BAD_REQUEST);
        }
        if (dto.discount() != null && dto.discount().signum() < 0) {
            throw new ApiException(ErrorMessages.INVALID_DISCOUNT, HttpStatus.BAD_REQUEST);
        }
        BuyerDto buyer = dto.buyer();
        if (buyer != null && ((!OrderValidation.isBlank(buyer.name()) && !OrderValidation.isValidName(buyer.name()))
                || (!OrderValidation.isBlank(buyer.email()) && !OrderValidation.isValidEmail(buyer.email()))
                || (!OrderValidation.isBlank(buyer.phone()) && !OrderValidation.isValidPhone(buyer.phone())))) {
            throw new ApiException(ErrorMessages.INVALID_OPTIONAL_BUYER, HttpStatus.BAD_REQUEST);
        }
        if (dto.deliveryType() == DeliveryType.ENVIO && !OrderValidation.isValid(dto.address())) {
            throw new ApiException(ErrorMessages.INVALID_ADDRESS, HttpStatus.BAD_REQUEST);
        }
        if (!OrderValidation.isValidNotes(dto.notes())) {
            throw new ApiException(ErrorMessages.INVALID_NOTES, HttpStatus.BAD_REQUEST);
        }
        return new ArrayList<>(merged.values());
    }

    private static Order buildLocalOrder(String number, LocalOrderDto dto, List<LocalOrderItemDto> items,
                                         ReservationDto reservation) throws ApiException {
        Order order = new Order();
        order.setNumber(number);
        order.setChannel(SalesChannel.LOCAL);
        order.setPaymentMethod(dto.paymentMethod());
        OrderValidation.setBuyer(order, dto.buyer());
        DeliveryType delivery = dto.deliveryType() == null ? DeliveryType.RETIRO : dto.deliveryType();
        order.setDeliveryType(delivery);
        if (delivery == DeliveryType.ENVIO) {
            order.setAddress(OrderValidation.toAddress(dto.address()));
        }
        order.setNotes(OrderValidation.trimToNull(dto.notes()));

        List<String> withoutPrice = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            ReservedItemDto r = reservation.items().get(i);
            BigDecimal price = items.get(i).price() != null ? items.get(i).price() : r.unitPrice();
            if (price == null) {
                withoutPrice.add(r.name());
                continue;
            }
            OrderItem item = OrderService.item(r, items.get(i).quantity(), price);
            subtotal = subtotal.add(item.getSubtotal());
            order.addItem(item);
        }
        if (!withoutPrice.isEmpty()) {
            throw new ApiException(ErrorMessages.PRICE_REQUIRED.formatted(String.join(", ", withoutPrice)), HttpStatus.BAD_REQUEST);
        }
        BigDecimal discount = dto.discount() == null ? BigDecimal.ZERO : dto.discount();
        if (discount.compareTo(subtotal) > 0) {
            throw new ApiException(ErrorMessages.INVALID_DISCOUNT, HttpStatus.BAD_REQUEST);
        }
        order.setDiscount(discount);
        order.setTotal(subtotal.subtract(discount));

        List<String> shortages = reservation.shortages() == null ? List.of() : reservation.shortages();
        order.setStockReview(!shortages.isEmpty());
        Long admin = SecurityUtil.currentUserId();
        String reason = shortages.isEmpty() ? LOCAL_SALE_REASON : LOCAL_SALE_REASON + ". Sin stock: " + String.join(", ", shortages);
        order.changeStatus(OrderStatus.PAGADO, admin, truncate(reason));
        if (dto.status() == OrderStatus.ENTREGADO) {
            order.changeStatus(OrderStatus.ENTREGADO, admin, null);
        }
        return order;
    }

    private static String truncate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    // from y to son dias de la tienda (SalesTime), no del servidor
    static Specification<Order> specification(Filter filter, SalesTime time) throws ApiException {
        OrderStatus status = parse(OrderStatus.class, filter.status(), "status");
        SalesChannel channel = parse(SalesChannel.class, filter.channel(), "channel");
        PaymentMethod paymentMethod = parse(PaymentMethod.class, filter.paymentMethod(), "paymentMethod");
        LocalDate from = parseDate(filter.from(), "from");
        LocalDate to = parseDate(filter.to(), "to");
        String q = filter.q() == null || filter.q().isBlank() ? null : filter.q().trim();

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (channel != null) predicates.add(cb.equal(root.get("channel"), channel));
            if (paymentMethod != null) predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            if (filter.stockReview() != null) predicates.add(cb.equal(root.get("stockReview"), filter.stockReview()));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), time.startOfDay(from)));
            if (to != null) predicates.add(cb.lessThan(root.get("createdAt"), time.startOfDay(to.plusDays(1))));
            if (q != null) {
                // Por numero (exacto, sin importar mayusculas) o por parte del email
                predicates.add(cb.or(
                        cb.equal(root.get("number"), q.toUpperCase(Locale.ROOT)),
                        cb.like(root.get("buyerEmail"), "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%", '\\')));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String name) throws ApiException {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorMessages.INVALID_FILTER.formatted(name), HttpStatus.BAD_REQUEST);
        }
    }

    private static LocalDate parseDate(String value, String name) throws ApiException {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorMessages.INVALID_FILTER.formatted(name), HttpStatus.BAD_REQUEST);
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
