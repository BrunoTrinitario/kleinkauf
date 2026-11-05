package com.kleinkauf.product.services;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.product.constants.ErrorMessages;
import com.kleinkauf.product.dto.store.SocialLinks;
import com.kleinkauf.product.dto.store.StoreDto;
import com.kleinkauf.product.dto.store.UpdateStoreDto;
import com.kleinkauf.product.entities.StoreSettings;
import com.kleinkauf.product.entities.StoreTheme;
import com.kleinkauf.product.repositories.StoreSettingsRepository;
import com.kleinkauf.product.util.FileManager;
import com.kleinkauf.product.util.SocialLinkNormalizer;
import com.kleinkauf.product.util.SocialLinkNormalizer.Network;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/*
Store settings: name, color theme, icon (and whether the icon goes in the header and as the favicon) and social
networks.
Until the administrator changes them the defaults apply: the name derived from the store identifier, the OCEAN
theme and no icon. The icon is stored and served like product images.
 */
@Service
public class StoreSettingsService {

    static final int MAX_NAME_LENGTH = 60;

    @Autowired
    private StoreSettingsRepository repository;

    @Autowired
    private FileManager fileManager;

    public StoreDto get() {
        return toDto(current());
    }

    public StoreDto update(UpdateStoreDto dto) throws ApiException {
        String name = dto.name() == null || dto.name().isBlank() ? null : dto.name().trim();
        if (name != null && name.length() > MAX_NAME_LENGTH) {
            throw new ApiException(ErrorMessages.INVALID_STORE_NAME, HttpStatus.BAD_REQUEST);
        }
        StoreTheme theme = dto.theme() == null ? null : parseTheme(dto.theme());
        // Validated before saving anything: an invalid link does not leave the rest half saved
        SocialLinks socials = dto.socials();
        String instagram = socials == null ? null : social(Network.INSTAGRAM, "Instagram", socials.instagram());
        String x = socials == null ? null : social(Network.X, "X", socials.x());
        String facebook = socials == null ? null : social(Network.FACEBOOK, "Facebook", socials.facebook());
        String tiktok = socials == null ? null : social(Network.TIKTOK, "TikTok", socials.tiktok());
        return toDto(save(settings -> {
            if (theme != null) settings.setTheme(theme.name());
            settings.setName(name);
            if (dto.showIconInHeader() != null) settings.setShowIconInHeader(dto.showIconInHeader());
            if (dto.showIconAsFavicon() != null) settings.setShowIconAsFavicon(dto.showIconAsFavicon());
            if (socials != null) {
                if (socials.instagram() != null) settings.setInstagramUrl(instagram);
                if (socials.x() != null) settings.setXUrl(x);
                if (socials.facebook() != null) settings.setFacebookUrl(facebook);
                if (socials.tiktok() != null) settings.setTiktokUrl(tiktok);
            }
        }));
    }

    // The profile link, or null if blank (removes the network). 400 if it is not from that network
    private static String social(Network network, String label, String value) throws ApiException {
        if (value == null || value.isBlank()) {
            return null;
        }
        String link = SocialLinkNormalizer.normalize(network, value);
        if (link == null) {
            throw new ApiException(ErrorMessages.INVALID_SOCIAL_LINK.formatted(label), HttpStatus.BAD_REQUEST);
        }
        return link;
    }

    // Replaces the icon (the previous file is deleted from disk). Same rules as product images: up to 1 MB and JPG,
    // PNG, WEBP or GIF by content (no SVG, which can carry scripts)
    public StoreDto saveIcon(MultipartFile file) throws ApiException {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorMessages.STORE_ICON_REQUIRED, HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > ProductService.MAX_IMAGE_BYTES) {
            throw new ApiException(ErrorMessages.IMAGE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
        }
        try {
            if (FileManager.imageExtension(file.getBytes()) == null) {
                throw new ApiException(ErrorMessages.INVALID_IMAGE, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            }
        } catch (IOException e) {
            throw new ApiException(ErrorMessages.INVALID_IMAGE, HttpStatus.BAD_REQUEST);
        }

        String icon = Paths.get(fileManager.SaveAllImages(List.of(file)).get(0)).getFileName().toString();
        AtomicReference<String> previous = new AtomicReference<>();
        StoreDto saved = toDto(save(settings -> {
            previous.set(settings.getIcon());
            settings.setIcon(icon);
        }));
        if (previous.get() != null) {
            fileManager.deleteImage(previous.get());
        }
        return saved;
    }

    public StoreDto deleteIcon() {
        if (current().getIcon() == null) {
            return toDto(current());
        }
        AtomicReference<String> previous = new AtomicReference<>();
        StoreDto saved = toDto(save(settings -> {
            previous.set(settings.getIcon());
            settings.setIcon(null);
        }));
        if (previous.get() != null) {
            fileManager.deleteImage(previous.get());
        }
        return saved;
    }

    private StoreSettings current() {
        return repository.findById(StoreSettings.SINGLETON_ID).orElseGet(StoreSettings::new);
    }

    /*
    Applies the change to the store row and saves it. If the row does not exist yet and two requests create it at the
    same time (e.g. uploading the icon and saving the theme), the second one hits the fixed id: it re-reads the row the
    other one created and applies the change there, instead of leaving two rows
     */
    private StoreSettings save(Consumer<StoreSettings> change) {
        try {
            return applyAndSave(current(), change);
        } catch (DataIntegrityViolationException e) {
            return applyAndSave(current(), change);
        }
    }

    private StoreSettings applyAndSave(StoreSettings settings, Consumer<StoreSettings> change) {
        change.accept(settings);
        settings.setUpdatedAt(LocalDateTime.now());
        return repository.saveAndFlush(settings);
    }

    private static StoreTheme parseTheme(String theme) throws ApiException {
        try {
            return StoreTheme.valueOf(theme.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorMessages.INVALID_STORE_THEME, HttpStatus.BAD_REQUEST);
        }
    }

    private static StoreDto toDto(StoreSettings s) {
        String name = s.getName() != null ? s.getName() : defaultName(TenantContext.getCurrentTenant());
        return new StoreDto(name, s.getName(), knownTheme(s.getTheme()).name(), s.getIcon(),
                s.isShowIconInHeader(), s.isShowIconAsFavicon(),
                new SocialLinks(s.getInstagramUrl(), s.getXUrl(), s.getFacebookUrl(), s.getTiktokUrl()));
    }

    // A theme that no longer exists (or a value set by hand) is shown as the default one
    private static StoreTheme knownTheme(String theme) {
        try {
            return theme == null ? StoreTheme.OCEAN : StoreTheme.valueOf(theme);
        } catch (IllegalArgumentException e) {
            return StoreTheme.OCEAN;
        }
    }

    // Default name from the schema: "mi_tienda" -> "Mi tienda". "Tienda" is shown to customers, so it stays in Spanish
    static String defaultName(String schema) {
        if (schema == null || schema.isBlank()) {
            return "Tienda";
        }
        String spaced = schema.replace('_', ' ').trim();
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }
}
