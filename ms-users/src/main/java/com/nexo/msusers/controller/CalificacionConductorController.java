package com.nexo.msusers.controller;

import com.nexo.msusers.client.PedidoClient;
import com.nexo.msusers.dto.CalificacionConductorRequestDTO;
import com.nexo.msusers.dto.CalificacionConductorResponseDTO;
import com.nexo.msusers.entity.CalificacionConductor;
import com.nexo.msusers.entity.Conductor;
import com.nexo.msusers.entity.Usuario;
import com.nexo.msusers.repository.CalificacionConductorRepository;
import com.nexo.msusers.repository.ConductorRepository;
import com.nexo.msusers.repository.UsuarioRepository;
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
@RequestMapping("/conductores")
public class CalificacionConductorController {

    private final CalificacionConductorRepository calificacionRepository;
    private final ConductorRepository conductorRepository;
    private final UsuarioRepository usuarioRepository;
    private final PedidoClient pedidoClient;

    public CalificacionConductorController(CalificacionConductorRepository calificacionRepository,
                                            ConductorRepository conductorRepository,
                                            UsuarioRepository usuarioRepository,
                                            PedidoClient pedidoClient) {
        this.calificacionRepository = calificacionRepository;
        this.conductorRepository = conductorRepository;
        this.usuarioRepository = usuarioRepository;
        this.pedidoClient = pedidoClient;
    }

    @GetMapping("/{conductorId}/calificaciones")
    public List<CalificacionConductorResponseDTO> listar(@PathVariable UUID conductorId) {
        return calificacionRepository.findByConductorId(conductorId)
                .stream().map(CalificacionConductorResponseDTO::desde).collect(Collectors.toList());
    }

    @PostMapping("/por-sub/{conductorSub}/calificaciones")
    public ResponseEntity<CalificacionConductorResponseDTO> calificarPorSub(
            @PathVariable String conductorSub,
            @Valid @RequestBody CalificacionConductorRequestDTO request,
            JwtAuthenticationToken auth) {

        Usuario usuarioConductor = usuarioRepository.findByCognitoSub(conductorSub)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conductor no encontrado"));

        Conductor conductor = conductorRepository.findByUsuarioId(usuarioConductor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil de conductor no encontrado"));

        if (calificacionRepository.existsByPedidoId(request.pedidoId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya calificaste al conductor de este pedido");
        }

        Jwt jwt = (Jwt) auth.getPrincipal();
        UUID usuarioId = UUID.fromString(jwt.getSubject());

        boolean valido = pedidoClient.pedidoCompletadoValido(request.pedidoId(), usuarioId);
        if (!valido) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo podés calificar conductores de pedidos que hayas completado");
        }

        CalificacionConductor calificacion = new CalificacionConductor();
        calificacion.setConductor(conductor);
        calificacion.setUsuarioId(usuarioId);
        calificacion.setPedidoId(request.pedidoId());
        calificacion.setPuntaje(request.puntaje());
        calificacion.setComentario(request.comentario());

        CalificacionConductor guardada = calificacionRepository.save(calificacion);
        return ResponseEntity.status(HttpStatus.CREATED).body(CalificacionConductorResponseDTO.desde(guardada));
    }
}