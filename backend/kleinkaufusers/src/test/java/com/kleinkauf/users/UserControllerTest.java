package com.kleinkauf.users;

import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.users.controllers.UserController;
import com.kleinkauf.users.services.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
Endpoints with {id} in the path: the id from the URL must reach the service
(the parameters used to be called userId and Spring responded 500).
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

	private static final long USER_ID = 7L;

	@Mock
	private UserService userService;

	@InjectMocks
	private UserController userController;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
	}

	@Test
	void giveRoleToUser_usesThePathId() throws Exception {
		mockMvc.perform(post("/api/v1/users/{id}/roles", USER_ID).param("roleName", "EMPLEADO"))
				.andExpect(status().isOk());

		verify(userService).addRoleToUser(USER_ID, "EMPLEADO");
	}

	@Test
	void removeRoleFromUser_usesThePathId() throws Exception {
		mockMvc.perform(delete("/api/v1/users/{id}/roles", USER_ID).param("roleName", "EMPLEADO"))
				.andExpect(status().isOk());

		verify(userService).deleteRoleFromUser(USER_ID, "EMPLEADO");
	}

	@Test
	void updateUser_usesThePathId() throws Exception {
		mockMvc.perform(patch("/api/v1/users/{id}", USER_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Ana\",\"password\":\"secreta\"}"))
				.andExpect(status().isOk());

		verify(userService).updateUser(USER_ID, "Ana", "secreta");
	}

	@Test
	void getUserRoles_usesThePathId() throws Exception {
		when(userService.getUserRoles(USER_ID)).thenReturn(List.of(new RoleDto("ADMINISTRADOR", 1)));

		mockMvc.perform(get("/api/v1/users/{id}/roles", USER_ID))
				.andExpect(status().isOk());

		verify(userService).getUserRoles(USER_ID);
	}
}
