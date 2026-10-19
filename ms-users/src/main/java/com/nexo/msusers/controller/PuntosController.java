package com.nexo.msusers.controller;

import com.nexo.msusers.repository.MovimientoPuntosRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/puntos")
public class PuntosController {

    private final MovimientoPuntosRepository movimientos;

    public PuntosController(MovimientoPuntosRepository movimientos) {
        this.movimientos = movimientos;
    }

    @GetMapping("/mios")
    public Map<String, Long> misPuntos(JwtAuthenticationToken auth) {
        UUID usuarioId = UUID.fromString(((Jwt) auth.getPrincipal()).getSubject());
        return Map.of("saldo", movimientos.saldoDe(usuarioId));
    }
}