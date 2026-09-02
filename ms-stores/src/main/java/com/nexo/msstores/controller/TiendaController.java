package com.nexo.msstores.controller;

import com.nexo.msstores.dto.RechazoRequestDTO;
import com.nexo.msstores.dto.TiendaRequestDTO;
import com.nexo.msstores.dto.TiendaResponseDTO;
import com.nexo.msstores.entity.Tienda;
import com.nexo.msstores.repository.TiendaRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tiendas")
public class TiendaController {

    private final TiendaRepository tiendaRepository;

    public TiendaController(TiendaRepository tiendaRepository) {
        this.tiendaRepository = tiendaRepository;
    }

    // E1-H1: público, solo tiendas aprobadas — no requiere JWT (se configura en SecurityConfig)
    @GetMapping
    public List<TiendaResponseDTO> listarAprobadas() {
        return tiendaRepository.findByEstado(Tienda.Estado.APPROVED)
                .stream().map(TiendaResponseDTO::desde).collect(Collectors.toList());
    }

    // Público: detalle de una tienda por ID — usado también por ms-products y ms-orders
    @GetMapping("/{id}")
    public TiendaResponseDTO obtenerPorId(@PathVariable UUID id) {
        Tienda tienda = tiendaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
        return TiendaResponseDTO.desde(tienda);
    }

    // E3-H1: cualquier usuario autenticado puede solicitar el registro de una tienda
    @PostMapping
    public ResponseEntity<TiendaResponseDTO> solicitarRegistro(
            @Valid @RequestBody TiendaRequestDTO request,
            JwtAuthenticationToken auth) {

        UUID ownerId = obtenerUserId(auth);

        Tienda tienda = new Tienda();
        tienda.setOwnerId(ownerId);
        tienda.setNombre(request.nombre());
        tienda.setDescripcion(request.descripcion());
        tienda.setDireccion(request.direccion());
        tienda.setLatitud(request.latitud());
        tienda.setLongitud(request.longitud());
        tienda.setLogoUrl(request.logoUrl());
        tienda.setHorario(request.horario());
        tienda.setMontoMinimo(request.montoMinimo());

        Tienda guardada = tiendaRepository.save(tienda);
        return ResponseEntity.status(HttpStatus.CREATED).body(TiendaResponseDTO.desde(guardada));
    }

    // E3-H13: el dueño edita los datos generales de su tienda
    @PutMapping("/{id}")
    public ResponseEntity<TiendaResponseDTO> editar(
            @PathVariable UUID id,
            @Valid @RequestBody TiendaRequestDTO request,
            JwtAuthenticationToken auth) {

        UUID ownerId = obtenerUserId(auth);
        Tienda tienda = tiendaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));

        if (!tienda.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No sos el dueño de esta tienda");
        }

        tienda.setNombre(request.nombre());
        tienda.setDescripcion(request.descripcion());
        tienda.setDireccion(request.direccion());
        tienda.setLatitud(request.latitud());
        tienda.setLongitud(request.longitud());
        tienda.setLogoUrl(request.logoUrl());
        tienda.setHorario(request.horario());
        tienda.setMontoMinimo(request.montoMinimo());

        return ResponseEntity.ok(TiendaResponseDTO.desde(tiendaRepository.save(tienda)));
    }

    // E4-H2: solo administradores ven las solicitudes pendientes
    @GetMapping("/pendientes")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<TiendaResponseDTO> listarPendientes() {
        return tiendaRepository.findByEstado(Tienda.Estado.PENDING)
                .stream().map(TiendaResponseDTO::desde).collect(Collectors.toList());
    }

    // E4-H4: solo administradores aprueban
    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<TiendaResponseDTO> aprobar(@PathVariable UUID id) {
        Tienda tienda = obtenerPendiente(id);
        tienda.setEstado(Tienda.Estado.APPROVED);
        return ResponseEntity.ok(TiendaResponseDTO.desde(tiendaRepository.save(tienda)));
    }

    // E4-H5: solo administradores rechazan, con motivo obligatorio
    @PatchMapping("/{id}/rechazar")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<TiendaResponseDTO> rechazar(
            @PathVariable UUID id,
            @Valid @RequestBody RechazoRequestDTO request) {

        Tienda tienda = obtenerPendiente(id);
        tienda.setEstado(Tienda.Estado.REJECTED);
        tienda.setMotivoRechazo(request.motivo());
        return ResponseEntity.ok(TiendaResponseDTO.desde(tiendaRepository.save(tienda)));
    }

    // E3-H14: el dueño solo ve/edita su propia tienda
    @GetMapping("/mi-tienda")
    public List<TiendaResponseDTO> misTiendas(JwtAuthenticationToken auth) {
        UUID ownerId = obtenerUserId(auth);
        return tiendaRepository.findByOwnerId(ownerId)
                .stream().map(TiendaResponseDTO::desde).collect(Collectors.toList());
    }

    private Tienda obtenerPendiente(UUID id) {
        Tienda tienda = tiendaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
        if (tienda.getEstado() != Tienda.Estado.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya fue resuelta anteriormente");
        }
        return tienda;
    }

    private UUID obtenerUserId(JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        return UUID.fromString(jwt.getClaimAsString("sub"));
    }
}