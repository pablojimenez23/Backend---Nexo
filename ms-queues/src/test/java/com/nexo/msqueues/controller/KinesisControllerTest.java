package com.nexo.msqueues.controller;

import com.nexo.msqueues.service.KinesisAdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KinesisControllerTest {

    @Mock private KinesisAdminService servicio;

    private JwtAuthenticationToken token(String... grupos) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "u1")
                .claim("cognito:groups", List.of(grupos)).build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void elAdministradorListaLosStreams() {
        when(servicio.listar()).thenReturn(List.of("nexo-pedidos-stream"));

        assertEquals(List.of("nexo-pedidos-stream"),
                new KinesisController(servicio).listar(token("ADMIN")));
    }

    @Test
    void quienNoEsAdministradorRecibe403() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> new KinesisController(servicio).listar(token("CLIENTE")));
        assertEquals(403, e.getStatusCode().value());
        verifyNoInteractions(servicio);
    }

    @Test
    void unNombreInvalidoResponde400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> new KinesisController(servicio).registros("Mal_Nombre", 10, token("admin")));
        assertEquals(400, e.getStatusCode().value());
    }

    @Test
    void unLimiteFueraDeRangoResponde400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> new KinesisController(servicio).registros("nexo-pedidos-stream", 500, token("ADMIN")));
        assertEquals(400, e.getStatusCode().value());
    }

    @Test
    void conDatosValidosDelegaEnElServicio() {
        when(servicio.leerRegistros("nexo-pedidos-stream", 10)).thenReturn(List.of());

        assertTrue(new KinesisController(servicio).registros("nexo-pedidos-stream", 10, token("ADMIN")).isEmpty());
        verify(servicio).leerRegistros("nexo-pedidos-stream", 10);
    }
}