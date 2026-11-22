package com.nexo.msusers.controller;

import com.nexo.msusers.entity.CanjePuntos;
import com.nexo.msusers.service.CanjeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/** Endpoints que solo usa ms-orders (protegidos con X-Internal-Key, sin JWT). */
@RestController
@RequestMapping("/puntos/internal")
public class PuntosInternoController {

    public record CanjeRequest(UUID usuarioId, Long puntos) {
    }

    private final CanjeService canjes;

    @Value("${internal.api.key}")
    private String internalApiKey;

    public PuntosInternoController(CanjeService canjes) {
        this.canjes = canjes;
    }

    @PostMapping("/canjear")
    public Map<String, Object> canjear(
            @RequestBody CanjeRequest request,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {
        validarClave(key);
        if (request == null || request.usuarioId() == null || request.puntos() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan usuarioId o puntos");
        }
        CanjePuntos canje = canjes.canjear(request.usuarioId(), request.puntos());
        return Map.of("canjeId", canje.getId(), "saldo", canjes.saldoDisponible(request.usuarioId()));
    }

    @PostMapping("/canjes/{id}/revertir")
    public Map<String, Object> revertir(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {
        validarClave(key);
        CanjePuntos canje = canjes.revertir(id);
        return Map.of("canjeId", canje.getId(), "estado", canje.getEstado().name());
    }

    private void validarClave(String key) {
        if (key == null || !key.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a servicios internos");
        }
    }
}