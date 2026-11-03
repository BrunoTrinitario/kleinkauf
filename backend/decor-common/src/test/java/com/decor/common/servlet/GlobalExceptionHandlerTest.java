package com.decor.common.servlet;

import com.decor.common.servlet.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Los errores del pedido responden su codigo (antes caian en el 500 generico)
class GlobalExceptionHandlerTest {

    @RestController
    static class ItemsController {
        @GetMapping("/items/{id}")
        String get(@PathVariable("id") Long id) {
            return "item " + id;
        }

        @PutMapping("/items/{id}")
        String put(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
            return "ok";
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("se rompio");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ItemsController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void parametroConOtroTipo_400() throws Exception {
        // Ej. GET /products/all cuando /all ya no existe: cae en /{id} con un id que no es numero
        mockMvc.perform(get("/items/all"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid value for id"));
    }

    @Test
    void metodoQueLaRutaNoTiene_405() throws Exception {
        mockMvc.perform(post("/items/5"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().string("Method not allowed"));
    }

    @Test
    void cuerpoQueNoEsJson_400() throws Exception {
        mockMvc.perform(put("/items/5").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("The request body is missing or is not valid JSON"));
    }

    @Test
    void errorNoPrevisto_sigueSiendo500() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("se rompio"));
    }
}
