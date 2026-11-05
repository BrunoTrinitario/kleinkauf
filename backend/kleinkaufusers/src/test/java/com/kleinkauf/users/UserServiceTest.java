/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit5TestClass.java to edit this template
 */

package com.kleinkauf.users;

import java.util.List;
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

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.users.constants.ErrorMessages;
import com.kleinkauf.users.dtos.FullUserDto;
import com.kleinkauf.users.dtos.LoggedUser;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.services.UserService;
 
/**
 *
 * @author bruno.trinitario
 */
@SpringBootTest
@Transactional
public class UserServiceTest extends ContainersTest {

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() throws ApiException {
        Set<String> adminRoles = Set.of("ADMINISTRADOR");
        userService.createUser("admin", "admin@test.com", "password1", adminRoles);
    }

    @Test
    void createUser_createsUserWithRole() throws ApiException {
        Set<String> roles = Set.of("CLIENTE");
        FullUserDto user = userService.createUser("user", "user@test.com", "password1", roles);

        assertNotNull(user.id());
        assertEquals("user", user.name());
        assertEquals("user@test.com", user.email());
        assertTrue(roleTypes(user).contains("CLIENTE"));
    }

    @Test
    void createUser_duplicateEmail_throwsApiException() {
        ApiException exception = assertThrows(
            ApiException.class,
            () -> userService.createUser("admin2", "admin@test.com", "password1", Set.of("CLIENTE"))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorMessages.EMAIL_ALREADY_IN_USE, exception.getMessage());
    }

    @Test
    void getUserById_existingUser_returnsExpectedData() throws ApiException {
        FullUserDto created = userService.createUser("user", "get-id@test.com", "password1", Set.of("CLIENTE"));

        FullUserDto fetched = userService.getUserById(created.id());
        assertEquals(created.id(), fetched.id());
        assertEquals("user", fetched.name());
        assertEquals("get-id@test.com", fetched.email());
        assertTrue(roleTypes(fetched).contains("CLIENTE"));
    }

    @Test
    void getUserById_nonExistingUser_throwsApiException() {
        ApiException exception = assertThrows(ApiException.class, () -> userService.getUserById(99999L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals(ErrorMessages.USER_NOT_FOUND, exception.getMessage());
    }

    @Test
    void userHasRole_returnsTrueOrFalseAccordingToAssignedRoles() throws ApiException {
        FullUserDto created = userService.createUser("role-user", "role-user@test.com", "password1", Set.of("CLIENTE"));

        assertTrue(userService.userHasRole(created.id(), "CLIENTE"));
        assertFalse(userService.userHasRole(created.id(), "ADMINISTRADOR"));
    }

    @Test
    void deleteRoleFromUser_existingRole_removesRole() throws ApiException {
        FullUserDto created = userService.createUser("vip-user", "vip-user@test.com", "password1", Set.of("CLIENTE", "EMPLEADO"));

        boolean deleted = userService.deleteRoleFromUser(created.id(), "EMPLEADO");

        assertTrue(deleted);
        assertFalse(userService.userHasRole(created.id(), "EMPLEADO"));
        assertTrue(userService.userHasRole(created.id(), "CLIENTE"));
    }

    @Test
    void roleLifecycle_reactivateDeletedRoleWithoutDuplicates() throws ApiException {
        FullUserDto created = userService.createUser("role-cycle", "role-cycle@test.com", "password1", Set.of("CLIENTE"));

        assertFalse(userService.userHasRole(created.id(), "EMPLEADO"));

        FullUserDto withRole = userService.addRoleToUser(created.id(), "EMPLEADO");
        assertTrue(roleTypes(withRole).contains("EMPLEADO"));
        assertTrue(userService.userHasRole(created.id(), "EMPLEADO"));
        assertEquals(2, withRole.roles().size());

        boolean removed = userService.deleteRoleFromUser(created.id(), "EMPLEADO");
        assertTrue(removed);
        assertFalse(userService.userHasRole(created.id(), "EMPLEADO"));

        FullUserDto withRoleAgain = userService.addRoleToUser(created.id(), "EMPLEADO");
        assertTrue(roleTypes(withRoleAgain).contains("EMPLEADO"));
        assertTrue(userService.userHasRole(created.id(), "EMPLEADO"));
        assertEquals(2, withRoleAgain.roles().size());
        assertEquals(1, roleTypes(withRoleAgain).stream().filter("EMPLEADO"::equals).count());
    }

    @Test
    void updateUser_updatesNameAndPassword() throws ApiException {
        FullUserDto created = userService.createUser("updatable", "updatable@test.com", "password1", Set.of("CLIENTE"));

        FullUserDto updated = userService.updateUser(created.id(), "updated-name", "new-password1");

        assertEquals("updated-name", updated.name());

        ApiException oldCredentialsException = assertThrows(
            ApiException.class,
            () -> userService.login("updatable@test.com", "password1")
        );
        assertEquals(HttpStatus.UNAUTHORIZED, oldCredentialsException.getStatus());

        LoggedUser loggedUser = userService.login("updatable@test.com", "new-password1");
        assertNotNull(loggedUser.token());
    }

    @Test
    void login_validCredentials_returnsLoggedUser() throws ApiException {
        // Login is by email (case-insensitive)
        LoggedUser loggedUser = userService.login("Admin@Test.com", "password1");

        assertEquals("admin", loggedUser.name());
        assertEquals("admin@test.com", loggedUser.email());
        assertNotNull(loggedUser.id());
        assertNotNull(loggedUser.token());
    }

    @Test
    void login_invalidCredentials_throwsApiException() {
        ApiException exception = assertThrows(ApiException.class, () -> userService.login("admin@test.com", "wrong-password"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals(ErrorMessages.INVALID_CREDENTIALS, exception.getMessage());
    }

    @Test
    void deleteUserById_existingUser_deletesUser() throws ApiException {
        FullUserDto created = userService.createUser("to-delete", "to-delete@test.com", "password1", Set.of("CLIENTE"));

        boolean deleted = userService.deleteUserById(created.id());

        assertTrue(deleted);
        ApiException exception = assertThrows(ApiException.class, () -> userService.getUserById(created.id()));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    // Roles come with their permission mask (RoleDto): comparing the type is enough
    private static List<String> roleTypes(FullUserDto user) {
        return user.roles().stream().map(RoleDto::getType).toList();
    }
}
