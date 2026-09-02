package com.nexo.msusers;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class UsuarioControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void primerLoginCreaElUsuarioAutomaticamente() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mockMvc.perform(get("/usuarios/me")
                .with(jwt().jwt(jwt -> jwt
                        .claim("email", "test@nexo.cl")
                        .claim("name", "Usuario de Prueba")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@nexo.cl"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }
}