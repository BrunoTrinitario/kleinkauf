package com.kleinkauf.common.servlet;

import com.kleinkauf.common.servlet.exceptions.GlobalExceptionHandler;
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

// Request errors respond with their own status code (they used to fall into the generic 500)
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
    void parameterOfAnotherType_400() throws Exception {
        // E.g. GET /products/all when /all no longer exists: it hits /{id} with an id that is not a number
        mockMvc.perform(get("/items/all"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid value for id"));
    }

    @Test
    void methodTheRouteDoesNotHave_405() throws Exception {
        mockMvc.perform(post("/items/5"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().string("Method not allowed"));
    }

    @Test
    void bodyThatIsNotJson_400() throws Exception {
        mockMvc.perform(put("/items/5").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("The request body is missing or is not valid JSON"));
    }

    @Test
    void unexpectedError_isStill500() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("se rompio"));
    }
}
