package com.kleinkauf.product.entities;

import com.kleinkauf.common.audit.Audited;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// Store settings (name, color theme and icon): a single row per schema
@Entity
@Audited
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
public class StoreSettings {

    // Fixed id: if two requests create the settings at the same time, the second one hits the primary key instead of
    // leaving a second row (see StoreSettingsService.save)
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    // null: the name derived from the store identifier is shown
    @Column(length = 60)
    private String name;

    // A StoreTheme, stored as text rather than with @Enumerated: with the enum Hibernate creates a CHECK with the
    // values at that moment, and hbm2ddl update does not refresh it when a theme is added (saving it would fail)
    @ColumnDefault("'OCEAN'")
    @Column(length = 20, nullable = false)
    private String theme = StoreTheme.OCEAN.name();

    // File name under /products/images/ (like product images). null: no icon
    @Column(length = 255)
    private String icon;

    @ColumnDefault("true")
    @Column(name = "show_icon_in_header", nullable = false)
    private boolean showIconInHeader = true;

    @ColumnDefault("true")
    @Column(name = "show_icon_as_favicon", nullable = false)
    private boolean showIconAsFavicon = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
