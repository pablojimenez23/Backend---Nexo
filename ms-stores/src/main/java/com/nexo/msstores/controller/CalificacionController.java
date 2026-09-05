package com.nexo.msstores.controller;

import com.nexo.msstores.client.PedidoClient;
import com.nexo.msstores.dto.CalificacionRequestDTO;
import com.nexo.msstores.dto.CalificacionResponseDTO;
import com.nexo.msstores.entity.Calificacion;
import com.nexo.msstores.entity.Tienda;
import com.nexo.msstores.repository.CalificacionRepository;
import com.nexo.msstores.repository.TiendaRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tiendas/{tiendaId}/calificaciones")
public class CalificacionController {

    private final CalificacionRepository calificacionRepository;
    private final TiendaRepository tiendaRepository;
    private final PedidoClient pedidoClient;

    public CalificacionController(CalificacionRepository calificacionRepository,
                                   TiendaRepository tiendaRepository,
                                   PedidoClient pedidoClient) {
        this.calificacionRepository = calificacionRepository;
        this.tiendaRepository = tiendaRepository;
        this.pedidoClient = pedidoClient;
    }

    @GetMapping
    public List<CalificacionResponseDTO> listar(@PathVariable UUID tiendaId) {
        return calificacionRepository.findByTiendaId(tiendaId)
                .stream().map(CalificacionResponseDTO::desde).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<CalificacionResponseDTO> calificar(
            @PathVariable UUID tiendaId,
            @Valid @RequestBody CalificacionRequestDTO request,
            JwtAuthenticationToken auth) {

        Tienda tienda = tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));

        Jwt jwt = (Jwt) auth.getPrincipal();
        UUID usuarioId = UUID.fromString(jwt.getSubject());

        boolean valido = pedidoClient.pedidoCompletadoValido(request.pedidoId(), usuarioId, tiendaId);
        if (!valido) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo podés calificar tiendas de pedidos que hayas completado");
        }

        Calificacion calificacion = new Calificacion();
        calificacion.setTienda(tienda);
        calificacion.setUsuarioId(usuarioId);
        calificacion.setPuntaje(request.puntaje());
        calificacion.setComentario(request.comentario());

        Calificacion guardada = calificacionRepository.save(calificacion);
        return ResponseEntity.status(HttpStatus.CREATED).body(CalificacionResponseDTO.desde(guardada));
    }
}