package com.kleinkauf.product;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.product.dto.store.SocialLinks;
import com.kleinkauf.product.dto.store.StoreDto;
import com.kleinkauf.product.dto.store.UpdateStoreDto;
import com.kleinkauf.product.entities.StoreSettings;
import com.kleinkauf.product.entities.StoreTheme;
import com.kleinkauf.product.repositories.StoreSettingsRepository;
import com.kleinkauf.product.services.StoreSettingsService;
import com.kleinkauf.product.util.FileManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreSettingsServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Mock
    private StoreSettingsRepository repository;

    @Mock
    private FileManager fileManager;

    @InjectMocks
    private StoreSettingsService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("mi_tienda");
        lenient().when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void get_withoutSettings_usesNameFromIdentifierOceanThemeAndNoIcon() {
        when(repository.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        StoreDto store = service.get();

        assertEquals("Mi tienda", store.name());
        assertNull(store.customName());
        assertEquals("OCEAN", store.theme());
        assertNull(store.icon());
        assertTrue(store.showIconInHeader());
        assertTrue(store.showIconAsFavicon());
    }

    @Test
    void update_savesNameThemeAndOptions_blankNameFallsBackToDefault() throws ApiException {
        StoreSettings settings = new StoreSettings();
        when(repository.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        StoreDto store = service.update(new UpdateStoreDto("  Casa Roble  ", "forest", false, true, null));
        assertEquals("Casa Roble", store.name());
        assertEquals("Casa Roble", store.customName());
        assertEquals("FOREST", store.theme());
        assertFalse(store.showIconInHeader());
        assertTrue(store.showIconAsFavicon());

        store = service.update(new UpdateStoreDto("   ", null, null, null, null));
        assertEquals("Mi tienda", store.name());
        assertNull(store.customName());
        assertEquals("FOREST", store.theme(), "a null theme keeps the current one");
        assertFalse(store.showIconInHeader(), "a null field keeps the current value");
    }

    @Test
    void update_rejectsLongNameAndUnknownTheme() {
        lenient().when(repository.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(new StoreSettings()));

        ApiException longName = assertThrows(ApiException.class,
                () -> service.update(new UpdateStoreDto("x".repeat(61), null, null, null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, longName.getStatus());

        ApiException theme = assertThrows(ApiException.class,
                () -> service.update(new UpdateStoreDto("Casa", "neon", null, null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, theme.getStatus());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void update_socials_buildsLinksFromUsersKeepsNullsAndRemovesBlanks() throws ApiException {
        StoreSettings settings = new StoreSettings();
        settings.setFacebookUrl("https://www.facebook.com/casaroble");
        when(repository.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        StoreDto store = service.update(new UpdateStoreDto("Casa", null, null, null,
                new SocialLinks("@casa.roble", "https://twitter.com/casaroble", null, "casaroble")));

        assertEquals("https://www.instagram.com/casa.roble", store.socials().instagram());
        assertEquals("https://twitter.com/casaroble", store.socials().x());
        assertEquals("https://www.facebook.com/casaroble", store.socials().facebook(), "a null network keeps its link");
        assertEquals("https://www.tiktok.com/@casaroble", store.socials().tiktok());

        store = service.update(new UpdateStoreDto("Casa", null, null, null, new SocialLinks(" ", null, "", null)));
        assertNull(store.socials().instagram(), "a blank network is removed");
        assertNull(store.socials().facebook());
        assertEquals("https://www.tiktok.com/@casaroble", store.socials().tiktok());
    }

    // The links are shown to every customer: only profiles of that network
    @Test
    void update_socials_rejectsLinksFromOtherSites() {
        lenient().when(repository.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(new StoreSettings()));

        for (SocialLinks socials : List.of(
                new SocialLinks("https://instagram.com.phishing.io/casa", null, null, null),
                new SocialLinks(null, "https://example.com/casa", null, null),
                new SocialLinks(null, null, "javascript:alert(1)", null),
                new SocialLinks(null, null, null, "https://user:pass@tiktok.com/@casa"))) {
            ApiException ex = assertThrows(ApiException.class,
                    () -> service.update(new UpdateStoreDto("Casa", null, null, null, socials)));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void get_storedThemeNoLongerExists_returnsDefaultTheme() {
        StoreSettings settings = new StoreSettings();
        settings.setTheme("RETIRED");
        when(repository.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        assertEquals("OCEAN", service.get().theme());
    }

    @Test
    void update_acceptsDarkTheme() throws ApiException {
        when(repository.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(new StoreSettings()));

        assertEquals("NIGHT", service.update(new UpdateStoreDto(null, "night", null, null, null)).theme());
    }

    @Test
    void saveIcon_replacesPreviousIconAndDeletesItsFile() throws ApiException {
        StoreSettings settings = new StoreSettings();
        settings.setIcon("viejo.png");
        when(repository.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(fileManager.SaveAllImages(any())).thenReturn(List.of("/uploads/products/images/nuevo.png"));

        StoreDto store = service.saveIcon(new MockMultipartFile("file", "logo.png", "image/png", PNG));

        assertEquals("nuevo.png", store.icon());
        verify(fileManager).deleteImage("viejo.png");
    }

    @Test
    void saveIcon_rejectsNonImagesAndFilesOver1MB() {
        ApiException notImage = assertThrows(ApiException.class, () -> service.saveIcon(
                new MockMultipartFile("file", "logo.svg", "image/svg+xml", "<svg/>".getBytes())));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, notImage.getStatus());

        ApiException tooLarge = assertThrows(ApiException.class, () -> service.saveIcon(
                new MockMultipartFile("file", "logo.png", "image/png", new byte[1024 * 1024 + 1])));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, tooLarge.getStatus());
        verifyNoInteractions(fileManager);
    }

    @Test
    void deleteIcon_removesTheFile() {
        StoreSettings settings = new StoreSettings();
        settings.setIcon("logo.png");
        settings.setTheme(StoreTheme.GRAPE.name());
        when(repository.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        StoreDto store = service.deleteIcon();

        assertNull(store.icon());
        assertEquals("GRAPE", store.theme());
        verify(fileManager).deleteImage("logo.png");
    }

    // Two requests create the row at the same time: the second one hits the fixed id, re-reads the row the first one
    // created and applies its change there instead of leaving a second row
    @Test
    void update_concurrentFirstSave_retriesOnTheExistingRow() throws ApiException {
        StoreSettings createdByOtherRequest = new StoreSettings();
        createdByOtherRequest.setIcon("logo.png");
        when(repository.findById(StoreSettings.SINGLETON_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(createdByOtherRequest));
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StoreDto store = service.update(new UpdateStoreDto("Casa Roble", "rose", null, null, null));

        assertEquals("Casa Roble", store.name());
        assertEquals("ROSE", store.theme());
        assertEquals("logo.png", store.icon(), "keeps what the other request saved");
        verify(repository, times(2)).saveAndFlush(any());
    }
}
