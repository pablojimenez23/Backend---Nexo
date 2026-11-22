package com.nexo.msusers.controller;

import com.nexo.msusers.entity.CanjePuntos;
import com.nexo.msusers.repository.CanjePuntosRepository;
import com.nexo.msusers.repository.MovimientoPuntosRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resumen del programa de puntos para el panel de administración.
 * Solo lectura y solo totales: no expone saldos de clientes individuales.
 */
@RestController
@RequestMapping("/puntos/admin")
public class PuntosAdminController {

    private final MovimientoPuntosRepository movimientos;
    private final CanjePuntosRepository canjes;

    @Value("${nexo.puntos.pesos-por-punto-canje:10}")
    private long pesosPorPuntoCanje;

    public PuntosAdminController(MovimientoPuntosRepository movimientos, CanjePuntosRepository canjes) {
        this.movimientos = movimientos;
        this.canjes = canjes;
    }

    @GetMapping("/resumen")
    public Map<String, Object> resumen(JwtAuthenticationToken auth) {
        exigirAdmin((Jwt) auth.getPrincipal());

        long emitidos = movimientos.totalEmitidos();
        long canjeados = canjes.totalGlobalPorEstado(CanjePuntos.Estado.ACTIVO);
        long devueltos = canjes.totalGlobalPorEstado(CanjePuntos.Estado.REVERTIDO);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("puntosEmitidos", emitidos);
        respuesta.put("puntosCanjeados", canjeados);
        respuesta.put("puntosDevueltos", devueltos);
        respuesta.put("puntosVigentes", emitidos - canjeados);
        respuesta.put("pesosPorPunto", pesosPorPuntoCanje);
        respuesta.put("clientesConPuntos", movimientos.acumuladosPorUsuario().size());
        return respuesta;
    }

    /** Solo el grupo admin de Cognito (sin distinguir mayúsculas) puede ver el resumen. */
    private static void exigirAdmin(Jwt jwt) {
        List<String> grupos = jwt.getClaimAsStringList("cognito:groups");
        boolean esAdmin = grupos != null && grupos.stream().anyMatch(g -> g.equalsIgnoreCase("admin"));
        if (!esAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administradores");
        }
    }
}