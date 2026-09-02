package com.nexo.msproducts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ProductoControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void unUsuarioAutenticadoPuedePublicarUnProducto() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        String body = """
                {
                  "tiendaId": "%s",
                  "nombre": "Pizza Muzzarella",
                  "descripcion": "Grande, con extra queso",
                  "precio": 8000,
                  "stock": 20
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/productos")
                .with(jwt())
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Pizza Muzzarella"))
                .andExpect(jsonPath("$.disponible").value(true));
    }

    @Test
    void unInvitadoPuedeVerElDetalleDeUnProductoSinToken() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mockMvc.perform(get("/productos/" + UUID.randomUUID()))
                .andExpect(status().isNotFound()); // no existe, pero llegó SIN 401/403 — confirma que la ruta es pública
    }
}