package com.nexo.msorders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class PedidoControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void unUsuarioSinTokenNoPuedeVerPedidos() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mockMvc.perform(get("/pedidos/mios"))
                .andExpect(status().isUnauthorized()); // sin JWT, ms-orders no tiene rutas públicas
    }

    @Test
    void unUsuarioAutenticadoPuedeConsultarSusPedidos() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mockMvc.perform(get("/pedidos/mios").with(jwt()))
                .andExpect(status().isOk());
    }
}