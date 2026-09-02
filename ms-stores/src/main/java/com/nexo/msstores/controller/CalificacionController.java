package com.nexo.msstores.controller;

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

    public CalificacionController(CalificacionRepository calificacionRepository, TiendaRepository tiendaRepository) {
        this.calificacionRepository = calificacionRepository;
        this.tiendaRepository = tiendaRepository;
    }

    // E1-H13 / E3-H12: público, cualquiera puede leer las calificaciones
    @GetMapping
    public List<CalificacionResponseDTO> listar(@PathVariable UUID tiendaId) {
        return calificacionRepository.findByTiendaId(tiendaId)
                .stream().map(CalificacionResponseDTO::desde).collect(Collectors.toList());
    }

    // E2-H8: calificar una tienda (idealmente solo tras un pedido DELIVERED,
    // esa validación cruzada con ms-orders queda pendiente para una iteración futura)
    @PostMapping
    public ResponseEntity<CalificacionResponseDTO> calificar(
            @PathVariable UUID tiendaId,
            @Valid @RequestBody CalificacionRequestDTO request,
            JwtAuthenticationToken auth) {

        Tienda tienda = tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));

        Jwt jwt = (Jwt) auth.getPrincipal();
        UUID usuarioId = UUID.fromString(jwt.getClaimAsString("sub"));

        Calificacion calificacion = new Calificacion();
        calificacion.setTienda(tienda);
        calificacion.setUsuarioId(usuarioId);
        calificacion.setPuntaje(request.puntaje());
        calificacion.setComentario(request.comentario());

        Calificacion guardada = calificacionRepository.save(calificacion);
        return ResponseEntity.status(HttpStatus.CREATED).body(CalificacionResponseDTO.desde(guardada));
    }
}