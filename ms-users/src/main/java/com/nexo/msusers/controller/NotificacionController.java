package com.nexo.msusers.controller;

import com.nexo.msusers.dto.CrearNotificacionRequestDTO;
import com.nexo.msusers.dto.NotificacionResponseDTO;
import com.nexo.msusers.entity.Notificacion;
import com.nexo.msusers.repository.NotificacionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notificaciones")
public class NotificacionController {

    private final NotificacionRepository notificacionRepository;

    @Value("${internal.api.key}")
    private String internalApiKey;

    public NotificacionController(NotificacionRepository notificacionRepository) {
        this.notificacionRepository = notificacionRepository;
    }

    @GetMapping
    public List<NotificacionResponseDTO> misNotificaciones(JwtAuthenticationToken auth) {
        UUID usuarioId = obtenerSub(auth);
        return notificacionRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId)
                .stream().map(NotificacionResponseDTO::desde).collect(Collectors.toList());
    }

    @GetMapping("/no-leidas")
    public Map<String, Long> contarNoLeidas(JwtAuthenticationToken auth) {
        UUID usuarioId = obtenerSub(auth);
        long cantidad = notificacionRepository.countByUsuarioIdAndLeidaFalse(usuarioId);
        return Map.of("cantidad", cantidad);
    }

    @PatchMapping("/{id}/leer")
    public ResponseEntity<Void> marcarLeida(@PathVariable UUID id, JwtAuthenticationToken auth) {
        UUID usuarioId = obtenerSub(auth);
        Notificacion notificacion = notificacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada"));

        if (!notificacion.getUsuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenés acceso a esta notificación");
        }

        notificacion.setLeida(true);
        notificacionRepository.save(notificacion);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, JwtAuthenticationToken auth) {
        UUID usuarioId = obtenerSub(auth);
        Notificacion notificacion = notificacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada"));

        if (!notificacion.getUsuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenés acceso a esta notificación");
        }

        notificacionRepository.delete(notificacion);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal")
    public ResponseEntity<Void> crearInterna(
            @RequestBody CrearNotificacionRequestDTO request,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {

        if (key == null || !key.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a servicios internos");
        }

        Notificacion notificacion = new Notificacion();
        notificacion.setUsuarioId(request.usuarioId());
        notificacion.setTitulo(request.titulo());
        notificacion.setMensaje(request.mensaje());
        notificacionRepository.save(notificacion);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private UUID obtenerSub(JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        return UUID.fromString(jwt.getSubject());
    }
}