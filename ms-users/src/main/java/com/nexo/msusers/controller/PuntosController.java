package com.nexo.msusers.controller;

import com.nexo.msusers.service.SaldoPuntosService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/puntos")
public class PuntosController {

    private final SaldoPuntosService saldos;

    /** Cuántos pesos vale un punto al canjearlo (1 punto = $10). */
    @Value("${nexo.puntos.pesos-por-punto-canje:10}")
    private long pesosPorPuntoCanje;

    public PuntosController(SaldoPuntosService saldos) {
        this.saldos = saldos;
    }

    @GetMapping("/mios")
    public Map<String, Object> misPuntos(JwtAuthenticationToken auth) {
        UUID usuarioId = UUID.fromString(((Jwt) auth.getPrincipal()).getSubject());
        SaldoPuntosService.Saldo s = saldos.calcular(usuarioId);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("saldo", s.disponible());
        respuesta.put("pesosPorPuntoCanje", pesosPorPuntoCanje);
        respuesta.put("puntosPorVencer", s.porVencer());
        respuesta.put("puntosVencidos", s.vencidos());
        respuesta.put("proximoVencimiento", s.proximoVencimiento() == null ? null : s.proximoVencimiento().toString());
        respuesta.put("puntosProximoVencimiento", s.puntosProximoVencimiento());
        return respuesta;
    }
}