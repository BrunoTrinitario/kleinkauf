package com.decor.sales.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// Configuracion de ventas de la tienda: una sola fila por schema
@Entity
@Table(name = "sales_settings")
@Getter
@Setter
@NoArgsConstructor
public class SalesSettings {

    public static final int DEFAULT_EXPIRATION_HOURS = 48;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Datos para la transferencia
    @Column(length = 20)
    private String alias;

    @Column(length = 22)
    private String cbu;

    @Column(length = 100)
    private String holder;

    @Column(length = 100)
    private String bank;

    // Horas para subir el comprobante antes de que el pedido venza
    @ColumnDefault("48")
    @Column(name = "expiration_hours", nullable = false)
    private int expirationHours = DEFAULT_EXPIRATION_HOURS;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
