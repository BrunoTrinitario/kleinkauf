package com.decor.sales.services;

import com.decor.common.dto.stock.ReservationDto;
import com.decor.common.dto.stock.ReservationItemDto;
import com.decor.common.dto.stock.ReservationRequestDto;
import com.decor.common.dto.stock.ReservedItemDto;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.sales.constants.ErrorMessages;
import com.decor.sales.dto.AddressDto;
import com.decor.sales.dto.BuyerDto;
import com.decor.sales.dto.CheckoutDto;
import com.decor.sales.dto.LookupDto;
import com.decor.sales.dto.NewOrderDto;
import com.decor.sales.dto.NewOrderItemDto;
import com.decor.sales.dto.OrderCreatedDto;
import com.decor.sales.dto.OrderDto;
import com.decor.sales.dto.OrderSummaryDto;
import com.decor.sales.dto.PageDto;
import com.decor.sales.dto.PriceChangeDto;
import com.decor.sales.entities.Address;
import com.decor.sales.entities.DeliveryType;
import com.decor.sales.entities.Order;
import com.decor.sales.entities.OrderItem;
import com.decor.sales.entities.OrderStatus;
import com.decor.sales.entities.PaymentMethod;
import com.decor.sales.entities.SalesChannel;
import com.decor.sales.entities.SalesSettings;
import com.decor.sales.repositories.OrderRepository;
import com.decor.sales.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/*
Pedidos de la tienda online: checkout, crear, consulta del invitado y mis pedidos.
Crear un pedido:
1. Valida el pedido y que la tienda tenga configurados los datos de pago.
2. Reserva el stock en products con el numero del pedido (todo o nada, idempotente).
3. Compara los precios con los del carrito: si cambio alguno, devuelve el stock y responde 409.
4. Guarda el pedido. Si falla, devuelve el stock.
Si products no responde (timeout, caido) la reserva pudo haberse hecho: se devuelve igual,
products ignora la devolucion de una reserva que no existe y rechaza la que llegue despues.
 */
@Slf4j
@Service
public class OrderService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final int NUMBER_ATTEMPTS = 5;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9 +()-]{6,30}$");

    @Value("${sales.order.max-lines:30}")
    private int maxLines;

    @Value("${sales.order.max-quantity:20}")
    private int maxQuantity;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SalesSettingsService settingsService;

    @Autowired
    private ProductsClient productsClient;

    @Autowired
    private OrderNumberGenerator numberGenerator;

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

        ReservationDto reservation = reserve(number, items);
        List<PriceChangeDto> changes = priceChanges(items, reservation.items());
        if (!changes.isEmpty()) {
            releaseQuietly(number);
            throw new PricesChangedException(changes);
        }

        Order order = buildOrder(number, dto, items, reservation.items(), settings);
        try {
            order = orderRepository.save(order);
        } catch (RuntimeException e) {
            log.error("No se pudo guardar el pedido {}: se devuelve el stock", number, e);
            releaseQuietly(number);
            throw new ApiException(ErrorMessages.ORDER_NOT_CREATED, HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new OrderCreatedDto(order.getNumber(), order.getStatus(), order.getTotal(), order.getExpiresAt(),
                SalesSettingsService.paymentDetails(settings));
    }

    // Mismo 404 si no existe o si el email no coincide: no revela que numeros existen
    @Transactional(readOnly = true)
    public OrderDto lookup(LookupDto dto) throws ApiException {
        if (dto == null || dto.email() == null || dto.number() == null) {
            throw new ApiException(ErrorMessages.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        String email = dto.email().trim().toLowerCase(Locale.ROOT);
        Order order = orderRepository.findByNumber(dto.number().trim().toUpperCase(Locale.ROOT))
                .filter(o -> email.equals(o.getBuyerEmail()))
                .orElseThrow(() -> new ApiException(ErrorMessages.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
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

    // Valida el pedido y junta las lineas repetidas (mismo producto o combo)
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
        if (buyer == null || isBlank(buyer.name()) || buyer.name().trim().length() > 100
                || buyer.email() == null || buyer.email().trim().length() > 150 || !EMAIL.matcher(buyer.email().trim()).matches()
                || buyer.phone() == null || !PHONE.matcher(buyer.phone().trim()).matches()) {
            throw new ApiException(ErrorMessages.INVALID_BUYER, HttpStatus.BAD_REQUEST);
        }
        if (dto.deliveryType() == null) {
            throw new ApiException(ErrorMessages.INVALID_DELIVERY, HttpStatus.BAD_REQUEST);
        }
        if (dto.deliveryType() == DeliveryType.ENVIO && !isValid(dto.address())) {
            throw new ApiException(ErrorMessages.INVALID_ADDRESS, HttpStatus.BAD_REQUEST);
        }
        if (dto.notes() != null && dto.notes().length() > 500) {
            throw new ApiException(ErrorMessages.INVALID_NOTES, HttpStatus.BAD_REQUEST);
        }
        return new ArrayList<>(merged.values());
    }

    private static boolean isValid(AddressDto a) {
        if (a == null) return false;
        for (String required : new String[]{a.street(), a.number(), a.city(), a.province(), a.postalCode()}) {
            if (isBlank(required) || required.trim().length() > 100) return false;
        }
        return a.apartment() == null || a.apartment().trim().length() <= 100;
    }

    // Unico en la tienda. Con 31^8 combinaciones un choque es muy raro: se reintenta unas veces
    private String newNumber() throws ApiException {
        for (int i = 0; i < NUMBER_ATTEMPTS; i++) {
            String number = numberGenerator.next();
            if (!orderRepository.existsByNumber(number)) return number;
        }
        throw new ApiException(ErrorMessages.ORDER_NOT_CREATED, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private ReservationDto reserve(String number, List<NewOrderItemDto> items) throws ApiException {
        List<ReservationItemDto> lines = items.stream()
                .map(i -> new ReservationItemDto(i.type(), i.id(), i.quantity()))
                .toList();
        try {
            ReservationDto reservation = productsClient.reserve(new ReservationRequestDto(number, lines));
            if (reservation == null || reservation.items() == null || reservation.items().size() != items.size()) {
                throw new IllegalStateException("Respuesta de reserva invalida para el pedido " + number);
            }
            return reservation;
        } catch (HttpClientErrorException e) {
            // Sin stock, sin precio o no disponible: products no desconto nada (todo o nada)
            HttpStatus status = e.getStatusCode().value() == 400 ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
            throw new ApiException(e.getResponseBodyAsString(), status);
        } catch (RuntimeException e) {
            log.warn("No se pudo reservar el stock del pedido {}: {}", number, e.toString());
            releaseQuietly(number);
            throw new ApiException(ErrorMessages.ORDER_NOT_CREATED, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    // Si la devolucion tambien falla el stock queda reservado: se loguea para corregirlo a mano
    private void releaseQuietly(String number) {
        try {
            productsClient.release(number);
        } catch (RuntimeException e) {
            log.error("No se pudo devolver el stock del pedido {}: revisar los movimientos con esa referencia", number, e);
        }
    }

    // La reserva devuelve los items en el mismo orden que se pidieron
    private static List<PriceChangeDto> priceChanges(List<NewOrderItemDto> items, List<ReservedItemDto> reserved) {
        List<PriceChangeDto> changes = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            NewOrderItemDto item = items.get(i);
            ReservedItemDto current = reserved.get(i);
            if (item.price().compareTo(current.unitPrice()) != 0) {
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
        order.setBuyerName(dto.buyer().name().trim());
        order.setBuyerEmail(dto.buyer().email().trim().toLowerCase(Locale.ROOT));
        order.setBuyerPhone(dto.buyer().phone().trim());
        order.setUserId(SecurityUtil.currentUserId());
        order.setDeliveryType(dto.deliveryType());
        if (dto.deliveryType() == DeliveryType.ENVIO) {
            AddressDto a = dto.address();
            order.setAddress(new Address(a.street().trim(), a.number().trim(), trimToNull(a.apartment()),
                    a.city().trim(), a.province().trim(), a.postalCode().trim()));
        }
        order.setNotes(trimToNull(dto.notes()));

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            ReservedItemDto r = reserved.get(i);
            OrderItem item = new OrderItem();
            item.setType(r.type() != null ? r.type() : items.get(i).type());
            item.setItemId(r.id() != null ? r.id() : items.get(i).id());
            item.setName(r.name());
            item.setQuantity(items.get(i).quantity());
            item.setListPrice(r.unitPrice());
            item.setUnitPrice(r.unitPrice());
            item.setUnitCost(r.unitCost());
            item.setSubtotal(r.unitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            total = total.add(item.getSubtotal());
            order.addItem(item);
        }
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(total);
        order.setExpiresAt(LocalDateTime.now().plusHours(settings.getExpirationHours()));
        order.changeStatus(OrderStatus.PENDIENTE_PAGO, order.getUserId(), null);
        return order;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
