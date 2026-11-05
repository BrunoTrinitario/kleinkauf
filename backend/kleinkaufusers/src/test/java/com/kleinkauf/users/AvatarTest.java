package com.kleinkauf.users;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.dtos.FullUserDto;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.repositories.UserRepository;
import com.kleinkauf.users.services.UserService;
import com.kleinkauf.users.util.AvatarStorage;
import com.kleinkauf.users.util.CacheUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvatarTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AvatarStorage avatarStorage;

    @Mock
    private CacheUtil cacheUtil;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("mi_tienda");
        user = new User();
        user.setId(7L);
        user.setName("Ana");
        user.setEmail("ana@mail.com");
        user.setRoles(new HashSet<>());
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        lenient().when(roleRepository.findByUser_Id(7L)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setAvatar_savesTheNewPhotoAndDeletesThePreviousOne() throws ApiException {
        user.setAvatar("old.png");
        when(avatarStorage.save(any(), eq("png"))).thenReturn("new.png");

        FullUserDto dto = userService.setAvatar(7L, new MockMultipartFile("file", "me.png", "image/png", PNG));

        assertEquals("new.png", dto.avatar());
        assertEquals("new.png", user.getAvatar());
        verify(userRepository).save(user);
        verify(avatarStorage).delete("old.png");
    }

    @Test
    void setAvatar_rejectsNonImagesAndFilesOver1MB() {
        ApiException svg = assertThrows(ApiException.class, () -> userService.setAvatar(7L,
                new MockMultipartFile("file", "me.svg", "image/png", "<svg onload=\"alert(1)\"/>".getBytes())));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, svg.getStatus());

        ApiException large = assertThrows(ApiException.class, () -> userService.setAvatar(7L,
                new MockMultipartFile("file", "me.png", "image/png", new byte[1024 * 1024 + 1])));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, large.getStatus());

        ApiException empty = assertThrows(ApiException.class, () -> userService.setAvatar(7L,
                new MockMultipartFile("file", "me.png", "image/png", new byte[0])));
        assertEquals(HttpStatus.BAD_REQUEST, empty.getStatus());

        verifyNoInteractions(avatarStorage);
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteAvatar_removesThePhotoAndItsFile() throws ApiException {
        user.setAvatar("me.png");

        FullUserDto dto = userService.deleteAvatar(7L);

        assertNull(dto.avatar());
        assertNull(user.getAvatar());
        verify(avatarStorage).delete("me.png");
    }

    @Test
    void deleteAvatar_withoutPhoto_changesNothing() throws ApiException {
        userService.deleteAvatar(7L);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(avatarStorage);
    }

    @Test
    void deletingTheAccount_deletesThePhoto() throws ApiException {
        user.setAvatar("me.png");

        userService.deleteUserById(7L);

        assertNull(user.getAvatar());
        verify(userRepository).deleteById(7L);
        verify(avatarStorage).delete("me.png");
    }

    // Real files in a temporary folder
    @Test
    void storage_savesWithRandomNameAndOnlyDeletesInsideItsFolder(@TempDir Path folder) throws Exception {
        AvatarStorage storage = new AvatarStorage();
        ReflectionTestUtils.setField(storage, "avatarPath", folder.resolve("avatars").toString());
        Path outside = Files.writeString(folder.resolve("secret.txt"), "keep me");

        String first = storage.save(PNG, "png");
        String second = storage.save(PNG, "png");

        assertNotEquals(first, second);
        assertTrue(first.endsWith(".png"));
        assertArrayEquals(PNG, Files.readAllBytes(folder.resolve("avatars").resolve(first)));

        storage.delete("../secret.txt");
        storage.delete(null);
        assertTrue(Files.exists(outside), "a name with a path must not delete files outside the folder");

        storage.delete(first);
        assertFalse(Files.exists(folder.resolve("avatars").resolve(first)));
    }
}
