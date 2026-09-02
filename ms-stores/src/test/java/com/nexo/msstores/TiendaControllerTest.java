package com.nexo.msstores;

import com.nexo.msstores.entity.Tienda;
import com.nexo.msstores.repository.TiendaRepository;
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
class TiendaControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TiendaRepository tiendaRepository;

    @Test
    void unUsuarioAutenticadoPuedeSolicitarRegistroDeTienda() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        String body = """
                {
                  "nombre": "Pizzería Don Mario",
                  "descripcion": "Pizzas artesanales",
                  "direccion": "Av. Providencia 123",
                  "horario": "12:00-23:00",
                  "montoMinimo": 5000
                }
                """;

        mockMvc.perform(post("/tiendas")
                .with(jwt().jwt(jwt -> jwt.claim("sub", UUID.randomUUID().toString())))
                .contentType("application/json")
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Pizzería Don Mario"))
                .andExpect(jsonPath("$.estado").value("PENDING"));
    }

    @Test
    void unUsuarioSinRolAdminNoPuedeAprobarUnaTienda() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Tienda tienda = new Tienda();
        tienda.setNombre("Tienda de prueba");
        tienda.setDireccion("Dirección de prueba");
        tienda.setOwnerId(UUID.randomUUID());
        Tienda guardada = tiendaRepository.save(tienda);

        mockMvc.perform(patch("/tiendas/" + guardada.getId() + "/aprobar")
                .with(jwt()))
                .andExpect(status().isForbidden());
    }
}