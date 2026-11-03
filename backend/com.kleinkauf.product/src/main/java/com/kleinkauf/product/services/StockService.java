package com.kleinkauf.product.services;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.constants.ErrorMessages;
import com.kleinkauf.product.dto.stock.LowStockDto;
import com.kleinkauf.product.dto.stock.NewStockMovementDto;
import com.kleinkauf.product.dto.stock.StockMovementDto;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.StockMovement;
import com.kleinkauf.product.entities.StockMovementType;
import com.kleinkauf.product.repositories.ProductRepository;
import com.kleinkauf.product.repositories.StockMovementRepository;
import com.kleinkauf.product.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/*
Unico lugar donde cambia el stock de un producto: cada cambio queda
registrado como un StockMovement con el usuario que lo hizo.
 */
@Service
public class StockService {

    private static final String INITIAL_STOCK_REASON = "Stock inicial";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private CatalogCacheService catalogCache;

    @Autowired
    private StockEvents stockEvents;

    @Transactional
    public StockMovementDto registerMovement(Long productId, NewStockMovementDto dto) throws ApiException {
        validate(dto);

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));

        int delta = switch (dto.type()) {
            case ENTRADA -> dto.quantity();
            case SALIDA -> -dto.quantity();
            case AJUSTE -> dto.quantity() - product.getStock();
        };
        int stockBefore = product.getStock();
        int newStock = stockBefore + delta;
        if (newStock < 0) {
            throw new ApiException(ErrorMessages.INSUFFICIENT_STOCK, HttpStatus.CONFLICT);
        }

        product.setStock(newStock);
        productRepository.save(product);

        StockMovement saved = save(product, dto.type(), delta, dto.reason());
        // La disponibilidad del catalogo cambia con el stock (se aplica despues del commit)
        catalogCache.invalidate();
        // Panel en vivo de la tienda (tambien despues del commit)
        stockEvents.movement(product, saved);
        stockEvents.lowIfCrossed(product, stockBefore);
        return toDto(saved);
    }

    // Lo usa ProductService al crear un producto con stock
    public void registerInitialStock(Product product) {
        if (product.getStock() > 0) {
            save(product, StockMovementType.ENTRADA, product.getStock(), INITIAL_STOCK_REASON);
        }
    }

    public List<StockMovementDto> getMovements(Long productId) throws ApiException {
        if (!productRepository.existsById(productId)) {
            throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return stockMovementRepository.findByProduct_IdOrderByCreatedAtDescIdDesc(productId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<LowStockDto> getLowStock() {
        return productRepository.findLowStock()
                .stream()
                .map(p -> new LowStockDto(p.getId(), p.getName(), p.getStock(), p.getMinStock()))
                .toList();
    }

    private void validate(NewStockMovementDto dto) throws ApiException {
        if (dto.type() == null) {
            throw new ApiException(ErrorMessages.INVALID_STOCK_MOVEMENT_TYPE, HttpStatus.BAD_REQUEST);
        }
        boolean isAdjustment = dto.type() == StockMovementType.AJUSTE;
        if (dto.quantity() == null || dto.quantity() < 0 || (!isAdjustment && dto.quantity() == 0)) {
            throw new ApiException(ErrorMessages.INVALID_STOCK_QUANTITY, HttpStatus.BAD_REQUEST);
        }
        if (isAdjustment && (dto.reason() == null || dto.reason().isBlank())) {
            throw new ApiException(ErrorMessages.STOCK_ADJUSTMENT_REASON_REQUIRED, HttpStatus.BAD_REQUEST);
        }
    }

    private StockMovement save(Product product, StockMovementType type, int delta, String reason) {
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(type);
        movement.setQuantity(delta);
        movement.setStockAfter(product.getStock());
        movement.setReason(reason);
        movement.setUserId(SecurityUtil.currentUserId());
        return stockMovementRepository.save(movement);
    }

    private StockMovementDto toDto(StockMovement m) {
        return new StockMovementDto(
                m.getId(),
                m.getProduct().getId(),
                m.getType(),
                m.getQuantity(),
                m.getStockAfter(),
                m.getReason(),
                m.getUserId(),
                m.getCreatedAt()
        );
    }
}
