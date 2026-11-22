package com.nexo.msusers.controller;

import com.nexo.msusers.service.CanjeService;
import org.springframework.beans.factory.annotation.Value;
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

    private final CanjeService canjes;

    /** Cuántos pesos vale un punto al canjearlo (1 punto = $10). */
    @Value("${nexo.puntos.pesos-por-punto-canje:10}")
    private long pesosPorPuntoCanje;

    public PuntosController(CanjeService canjes) {
        this.canjes = canjes;
    }

    @GetMapping("/mios")
    public Map<String, Long> misPuntos(JwtAuthenticationToken auth) {
        UUID usuarioId = UUID.fromString(((Jwt) auth.getPrincipal()).getSubject());
        return Map.of(
                "saldo", canjes.saldoDisponible(usuarioId),
                "pesosPorPuntoCanje", pesosPorPuntoCanje);
    }
}