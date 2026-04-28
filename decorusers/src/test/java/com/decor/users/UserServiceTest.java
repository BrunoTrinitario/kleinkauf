/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit5TestClass.java to edit this template
 */

package com.decor.users;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import com.decor.users.constants.ErrorMessages;
import com.decor.users.dtos.FullUserDto;
import com.decor.users.dtos.LoggedUser;
import com.decor.users.exceptions.ApiException;
import com.decor.users.services.UserService;
 
/**
 *
 * @author bruno.trinitario
 */
@SpringBootTest
@Transactional
public class UserServiceTest {

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() throws ApiException {
        Set<String> adminRoles = Set.of("ADMINISTRADOR");
        userService.createUser("admin", "admin@test.com", "password", adminRoles);
    }

    @Test
    void createUser_createsUserWithRole() throws ApiException {
        Set<String> roles = Set.of("CLIENTE");
        FullUserDto user = userService.createUser("user", "user@test.com", "password", roles);

        assertNotNull(user.id());
        assertEquals("user", user.name());
        assertEquals("user@test.com", user.email());
        assertTrue(user.roles().contains("CLIENTE"));
        assertEquals(2, userService.getAllUsers().size());
    }

    @Test
    void createUser_duplicateEmail_throwsApiException() {
        ApiException exception = assertThrows(
            ApiException.class,
            () -> userService.createUser("admin2", "admin@test.com", "password", Set.of("CLIENTE"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(ErrorMessages.EMAIL_ALREADY_IN_USE, exception.getMessage());
    }

    @Test
    void getUserById_existingUser_returnsExpectedData() throws ApiException {
        FullUserDto created = userService.createUser("user", "get-id@test.com", "password", Set.of("CLIENTE"));

        FullUserDto fetched = userService.getUserById(created.id());
        assertEquals(created.id(), fetched.id());
        assertEquals("user", fetched.name());
        assertEquals("get-id@test.com", fetched.email());
        assertTrue(fetched.roles().contains("CLIENTE"));
    }

    @Test
    void getUserById_nonExistingUser_throwsApiException() {
        ApiException exception = assertThrows(ApiException.class, () -> userService.getUserById(99999L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals(ErrorMessages.USER_NOT_FOUND, exception.getMessage());
    }

    @Test
    void userHasRole_returnsTrueOrFalseAccordingToAssignedRoles() throws ApiException {
        FullUserDto created = userService.createUser("role-user", "role-user@test.com", "password", Set.of("CLIENTE"));

        assertTrue(userService.userHasRole(created.id(), "CLIENTE"));
        assertFalse(userService.userHasRole(created.id(), "ADMINISTRADOR"));
    }

    @Test
    void deleteRoleFromUser_existingRole_removesRole() throws ApiException {
        FullUserDto created = userService.createUser("vip-user", "vip-user@test.com", "password", Set.of("CLIENTE", "VIP"));

        boolean deleted = userService.deleteRoleFromUser(created.id(), "VIP");

        assertTrue(deleted);
        assertFalse(userService.userHasRole(created.id(), "VIP"));
        assertTrue(userService.userHasRole(created.id(), "CLIENTE"));
    }

    @Test
    void roleLifecycle_reactivateDeletedRoleWithoutDuplicates() throws ApiException {
        FullUserDto created = userService.createUser("role-cycle", "role-cycle@test.com", "password", Set.of("CLIENTE"));

        assertFalse(userService.userHasRole(created.id(), "VENDEDOR"));

        FullUserDto withRole = userService.addRoleToUser(created.id(), "VENDEDOR");
        assertTrue(withRole.roles().contains("VENDEDOR"));
        assertTrue(userService.userHasRole(created.id(), "VENDEDOR"));
        assertEquals(2, withRole.roles().size());

        boolean removed = userService.deleteRoleFromUser(created.id(), "VENDEDOR");
        assertTrue(removed);
        assertFalse(userService.userHasRole(created.id(), "VENDEDOR"));

        FullUserDto withRoleAgain = userService.addRoleToUser(created.id(), "VENDEDOR");
        assertTrue(withRoleAgain.roles().contains("VENDEDOR"));
        assertTrue(userService.userHasRole(created.id(), "VENDEDOR"));
        assertEquals(2, withRoleAgain.roles().size());
        assertEquals(1, withRoleAgain.roles().stream().filter("VENDEDOR"::equals).count());
    }

    @Test
    void updateUser_updatesNameAndPassword() throws ApiException {
        FullUserDto created = userService.createUser("updatable", "updatable@test.com", "password", Set.of("CLIENTE"));

        FullUserDto updated = userService.updateUser(created.id(), "updated-name", "new-password");

        assertEquals("updated-name", updated.name());

        ApiException oldCredentialsException = assertThrows(
            ApiException.class,
            () -> userService.login("updated-name", "password")
        );
        assertEquals(HttpStatus.UNAUTHORIZED, oldCredentialsException.getStatus());

        LoggedUser loggedUser = userService.login("updated-name", "new-password");
        assertNotNull(loggedUser.token());
    }

    @Test
    void login_validCredentials_returnsLoggedUser() throws ApiException {
        LoggedUser loggedUser = userService.login("admin", "password");

        assertEquals("admin", loggedUser.name());
        assertEquals("admin@test.com", loggedUser.email());
        assertNotNull(loggedUser.id());
        assertNotNull(loggedUser.token());
    }

    @Test
    void login_invalidCredentials_throwsApiException() {
        ApiException exception = assertThrows(ApiException.class, () -> userService.login("admin", "wrong-password"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals(ErrorMessages.INVALID_CREDENTIALS, exception.getMessage());
    }

    @Test
    void deleteUserById_existingUser_deletesUser() throws ApiException {
        FullUserDto created = userService.createUser("to-delete", "to-delete@test.com", "password", Set.of("CLIENTE"));

        boolean deleted = userService.deleteUserById(created.id());

        assertTrue(deleted);
        ApiException exception = assertThrows(ApiException.class, () -> userService.getUserById(created.id()));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

}