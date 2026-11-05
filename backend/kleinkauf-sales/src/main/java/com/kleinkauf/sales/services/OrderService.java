package com.kleinkauf.sales.services;

import com.kleinkauf.common.dto.stock.ReservationDto;
import com.kleinkauf.common.dto.stock.ReservationItemDto;
import com.kleinkauf.common.dto.stock.ReservedItemDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.sales.constants.ErrorMessages;
import com.kleinkauf.sales.dto.BuyerDto;
import com.kleinkauf.sales.dto.CancelDto;
import com.kleinkauf.sales.dto.CheckoutDto;
import com.kleinkauf.sales.dto.LookupDto;
import com.kleinkauf.sales.dto.NewOrderDto;
import com.kleinkauf.sales.dto.NewOrderItemDto;
import com.kleinkauf.sales.dto.OrderCreatedDto;
import com.kleinkauf.sales.dto.OrderDto;
import com.kleinkauf.sales.dto.OrderSummaryDto;
import com.kleinkauf.sales.dto.PageDto;
import com.kleinkauf.sales.dto.PriceChangeDto;
import com.kleinkauf.sales.entities.DeliveryType;
import com.kleinkauf.sales.entities.Order;
import com.kleinkauf.sales.entities.OrderItem;
import com.kleinkauf.sales.entities.OrderStatus;
import com.kleinkauf.sales.entities.PaymentMethod;
import com.kleinkauf.sales.entities.PaymentReceipt;
import com.kleinkauf.sales.entities.SalesChannel;
import com.kleinkauf.sales.entities.SalesSettings;
import com.kleinkauf.sales.repositories.OrderRepository;
import com.kleinkauf.sales.util.ReceiptStorage;
import com.kleinkauf.sales.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
Online store orders: checkout, create, guest lookup, receipt, cancellation and my orders.
Creating an order:
1. Validates the order and that the store has the payment details configured.
2. Reserves the stock in products with the order number (all or nothing, idempotent).
3. Compares the prices with the cart's: if any changed, it returns the stock and responds 409.
4. Saves the order. If it fails, it returns the stock.
The receipt and the cancellation identify the guest by the order email.
 */
@Slf4j
@Service
public class OrderService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final int NUMBER_ATTEMPTS = 5;

    @Value("${sales.order.max-lines:30}")
    private int maxLines;

    @Value("${sales.order.max-quantity:20}")
    private int maxQuantity;

    @Value("${sales.receipts.max-per-order:5}")
    private int maxReceipts;

    @Value("${sales.receipts.max-size:5242880}")
    private long maxReceiptSize;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SalesSettingsService settingsService;

    @Autowired
    private OrderStock orderStock;

    @Autowired
    private OrderStatusService statusService;

    @Autowired
    private OrderNumberGenerator numberGenerator;

    @Autowired
    private ReceiptStorage receiptStorage;

    @Autowired
    private OrderEvents orderEvents;

    public CheckoutDto checkout() {
        SalesSettings settings = settingsService.current();
        boolean configured = SalesSettingsService.isPaymentConfigured(settings);
        return new CheckoutDto(configured, configured ? SalesSettingsService.paymentDetails(settings) : null,
                settings.getExpirationHours(), List.of(DeliveryType.values()), maxLines, maxQuantity);
    }

    public OrderCreatedDto create(NewOrderDto dto) throws ApiException, PricesChangedException {
        List<NewOrderItemDto> items = validate(dto);
        SalesSettings settings = settingsService.current();
        if (!SalesSettingsService.isPaymentConfigured(settings)) {
            throw new ApiException(ErrorMessages.PAYMENT_NOT_CONFIGURED, HttpStatus.CONFLICT);
        }
        String number = newNumber();

        List<ReservationItemDto> lines = items.stream()
                .map(i -> new ReservationItemDto(i.type(), i.id(), i.quantity()))
                .toList();
        ReservationDto reservation = orderStock.reserve(number, lines, false);
        List<PriceChangeDto> changes = priceChanges(items, reservation.items());
        if (!changes.isEmpty()) {
            orderStock.releaseQuietly(number);
            throw new PricesChangedException(changes);
        }

        Order order = buildOrder(number, dto, items, reservation.items(), settings);
        try {
            order = orderRepository.save(order);
        } catch (RuntimeException e) {
            log.error("No se pudo guardar el pedido {}: se devuelve el stock", number, e);
            orderStock.releaseQuietly(number);
            throw new ApiException(ErrorMessages.ORDER_NOT_CREATED, HttpStatus.SERVICE_UNAVAILABLE);
        }
        orderEvents.created(order);
        return new OrderCreatedDto(order.getNumber(), order.getStatus(), order.getTotal(), order.getExpiresAt(),
                SalesSettingsService.paymentDetails(settings));
    }

    @Transactional(readOnly = true)
    public OrderDto lookup(LookupDto dto) throws ApiException {
        if (dto == null) throw notFound();
        return OrderMapper.toBuyerDto(findForBuyer(dto.number(), dto.email()), settingsService.current());
    }

    /*
    Uploads the receipt: up to maxReceipts per order, JPG, PNG or PDF up to 5 MB, while the order
    awaits payment (PENDIENTE_PAGO or RECHAZADO) and has not expired. Moves to PAGO_EN_REVISION.
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderDto uploadReceipt(String number, String email, MultipartFile file) throws ApiException {
        Order order = findForBuyer(number, email);
        if (!order.getStatus().acceptsReceipt()
                || (order.getExpiresAt() != null && order.getExpiresAt().isBefore(LocalDateTime.now()))) {
            throw new ApiException(ErrorMessages.RECEIPT_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        if (order.getReceipts().size() >= maxReceipts) {
            throw new ApiException(ErrorMessages.TOO_MANY_RECEIPTS.formatted(maxReceipts), HttpStatus.CONFLICT);
        }
        byte[] bytes = readReceipt(file);
        ReceiptStorage.FileType type = ReceiptStorage.detect(bytes);
        if (type == null) {
            throw new ApiException(ErrorMessages.INVALID_RECEIPT, HttpStatus.BAD_REQUEST);
        }

        String path = receiptStorage.save(TenantContext.getCurrentTenant(), order.getNumber(), bytes, type);
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setPath(path);
        receipt.setContentType(type.contentType);
        receipt.setSize(bytes.length);
        order.addReceipt(receipt);
        order.changeStatus(OrderStatus.PAGO_EN_REVISION, SecurityUtil.currentUserId(), null);
        try {
            order = orderRepository.saveAndFlush(order);
        } catch (ObjectOptimisticLockingFailureException e) {
            receiptStorage.deleteQuietly(path);
            throw new ApiException(ErrorMessages.ORDER_CHANGED, HttpStatus.CONFLICT);
        } catch (RuntimeException e) {
            receiptStorage.deleteQuietly(path);
            throw e;
        }
        // Published after the commit
        orderEvents.receiptUploaded(order);
        return OrderMapper.toBuyerDto(order, settingsService.current());
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderDto cancel(String number, CancelDto dto) throws ApiException {
        Order order = findForBuyer(number, dto == null ? null : dto.email());
        order = statusService.cancelByBuyer(order, SecurityUtil.currentUserId());
        return OrderMapper.toBuyerDto(order, settingsService.current());
    }

    @Transactional(readOnly = true)
    public PageDto<OrderSummaryDto> mine(Integer page, Integer size) throws ApiException {
        PageRequest pageRequest = pageRequest(page, size);
        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDescIdDesc(SecurityUtil.currentUserId(), pageRequest);
        return toPage(orders);
    }

    static PageRequest pageRequest(Integer page, Integer size) throws ApiException {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorMessages.INVALID_PAGE, HttpStatus.BAD_REQUEST);
        }
        return PageRequest.of(pageNumber, pageSize);
    }

    static PageDto<OrderSummaryDto> toPage(Page<Order> orders) {
        return new PageDto<>(orders.getContent().stream().map(OrderMapper::toSummary).toList(),
                orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages());
    }

    // Unique within the store (in-store sales use it too). With 31^8 combinations a collision is very rare
    public String newNumber() throws ApiException {
        for (int i = 0; i < NUMBER_ATTEMPTS; i++) {
            String number = numberGenerator.next();
            if (!orderRepository.existsByNumber(number)) return number;
        }
        throw new ApiException(ErrorMessages.ORDER_NOT_CREATED, HttpStatus.SERVICE_UNAVAILABLE);
    }

    // Same 404 if it does not exist or the email does not match: does not reveal which numbers exist
    private Order findForBuyer(String number, String email) throws ApiException {
        if (number == null || email == null) throw notFound();
        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        return orderRepository.findByNumber(number.trim().toUpperCase(Locale.ROOT))
                .filter(o -> cleanEmail.equals(o.getBuyerEmail()))
                .orElseThrow(OrderService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorMessages.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    private byte[] readReceipt(MultipartFile file) throws ApiException {
        if (file == null || file.isEmpty() || file.getSize() > maxReceiptSize) {
            throw new ApiException(ErrorMessages.INVALID_RECEIPT, HttpStatus.BAD_REQUEST);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorMessages.INVALID_RECEIPT, HttpStatus.BAD_REQUEST);
        }
    }

    // Validates the order and merges repeated lines (same product or bundle)
    private List<NewOrderItemDto> validate(NewOrderDto dto) throws ApiException {
        String invalidItems = ErrorMessages.INVALID_ITEMS.formatted(maxLines, maxQuantity);
        if (dto == null || dto.items() == null || dto.items().isEmpty()) {
            throw new ApiException(invalidItems, HttpStatus.BAD_REQUEST);
        }
        Map<String, NewOrderItemDto> merged = new LinkedHashMap<>();
        for (NewOrderItemDto item : dto.items()) {
            if (item == null || item.type() == null || item.id() == null || item.quantity() == null
                    || item.quantity() < 1 || item.price() == null || item.price().signum() < 0) {
                throw new ApiException(invalidItems, HttpStatus.BAD_REQUEST);
            }
            merged.merge(item.type() + ":" + item.id(), item, (a, b) ->
                    new NewOrderItemDto(a.type(), a.id(), a.quantity() + b.quantity(), a.price()));
        }
        if (merged.size() > maxLines || merged.values().stream().anyMatch(i -> i.quantity() > maxQuantity)) {
            throw new ApiException(invalidItems, HttpStatus.BAD_REQUEST);
        }

        BuyerDto buyer = dto.buyer();
        if (buyer == null || !OrderValidation.isValidName(buyer.name()) || !OrderValidation.isValidEmail(buyer.email())
                || !OrderValidation.isValidPhone(buyer.phone())) {
            throw new ApiException(ErrorMessages.INVALID_BUYER, HttpStatus.BAD_REQUEST);
        }
        if (dto.deliveryType() == null) {
            throw new ApiException(ErrorMessages.INVALID_DELIVERY, HttpStatus.BAD_REQUEST);
        }
        if (dto.deliveryType() == DeliveryType.ENVIO && !OrderValidation.isValid(dto.address())) {
            throw new ApiException(ErrorMessages.INVALID_ADDRESS, HttpStatus.BAD_REQUEST);
        }
        if (!OrderValidation.isValidNotes(dto.notes())) {
            throw new ApiException(ErrorMessages.INVALID_NOTES, HttpStatus.BAD_REQUEST);
        }
        return new ArrayList<>(merged.values());
    }

    // The reservation returns the items in the same order they were requested
    private static List<PriceChangeDto> priceChanges(List<NewOrderItemDto> items, List<ReservedItemDto> reserved) {
        List<PriceChangeDto> changes = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            NewOrderItemDto item = items.get(i);
            ReservedItemDto current = reserved.get(i);
            if (current.unitPrice() == null || item.price().compareTo(current.unitPrice()) != 0) {
                changes.add(new PriceChangeDto(item.type(), item.id(), current.name(), item.price(), current.unitPrice()));
            }
        }
        return changes;
    }

    private static Order buildOrder(String number, NewOrderDto dto, List<NewOrderItemDto> items,
                                    List<ReservedItemDto> reserved, SalesSettings settings) {
        Order order = new Order();
        order.setNumber(number);
        order.setChannel(SalesChannel.ONLINE);
        order.setPaymentMethod(PaymentMethod.TRANSFERENCIA);
        OrderValidation.setBuyer(order, dto.buyer());
        order.setUserId(SecurityUtil.currentUserId());
        order.setDeliveryType(dto.deliveryType());
        if (dto.deliveryType() == DeliveryType.ENVIO) {
            order.setAddress(OrderValidation.toAddress(dto.address()));
        }
        order.setNotes(OrderValidation.trimToNull(dto.notes()));

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            ReservedItemDto r = reserved.get(i);
            OrderItem item = item(r, items.get(i).quantity(), r.unitPrice());
            total = total.add(item.getSubtotal());
            order.addItem(item);
        }
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(total);
        order.setExpiresAt(LocalDateTime.now().plusHours(settings.getExpirationHours()));
        order.changeStatus(OrderStatus.PENDIENTE_PAGO, order.getUserId(), null);
        return order;
    }

    // Line with the data at sale time. unitPrice: the one charged (in the store it may not be the list price)
    static OrderItem item(ReservedItemDto r, int quantity, BigDecimal unitPrice) {
        OrderItem item = new OrderItem();
        item.setType(r.type());
        item.setItemId(r.id());
        item.setName(r.name());
        item.setQuantity(quantity);
        item.setListPrice(r.unitPrice());
        item.setUnitPrice(unitPrice);
        item.setUnitCost(r.unitCost());
        item.setSubtotal(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        return item;
    }
}
