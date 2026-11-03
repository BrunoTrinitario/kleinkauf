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
Endpoints con {id} en la ruta: el id de la URL tiene que llegar al servicio
(antes los parametros se llamaban userId y Spring respondia 500).
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
	void giveRoleToUser_usaElIdDeLaRuta() throws Exception {
		mockMvc.perform(post("/api/v1/users/{id}/roles", USER_ID).param("roleName", "EMPLEADO"))
				.andExpect(status().isOk());

		verify(userService).addRoleToUser(USER_ID, "EMPLEADO");
	}

	@Test
	void removeRoleFromUser_usaElIdDeLaRuta() throws Exception {
		mockMvc.perform(delete("/api/v1/users/{id}/roles", USER_ID).param("roleName", "EMPLEADO"))
				.andExpect(status().isOk());

		verify(userService).deleteRoleFromUser(USER_ID, "EMPLEADO");
	}

	@Test
	void updateUser_usaElIdDeLaRuta() throws Exception {
		mockMvc.perform(patch("/api/v1/users/{id}", USER_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Ana\",\"password\":\"secreta\"}"))
				.andExpect(status().isOk());

		verify(userService).updateUser(USER_ID, "Ana", "secreta");
	}

	@Test
	void getUserRoles_usaElIdDeLaRuta() throws Exception {
		when(userService.getUserRoles(USER_ID)).thenReturn(List.of(new RoleDto("ADMINISTRADOR", 1)));

		mockMvc.perform(get("/api/v1/users/{id}/roles", USER_ID))
				.andExpect(status().isOk());

		verify(userService).getUserRoles(USER_ID);
	}
}
