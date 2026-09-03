package com.nexo.msstores.controller;

import com.nexo.msstores.client.NotificacionClient;
import com.nexo.msstores.client.ProductoClient;
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
    private final ProductoClient productoClient;
    private final NotificacionClient notificacionClient;

    public TiendaController(TiendaRepository tiendaRepository,
                             ProductoClient productoClient,
                             NotificacionClient notificacionClient) {
        this.tiendaRepository = tiendaRepository;
        this.productoClient = productoClient;
        this.notificacionClient = notificacionClient;
    }

    @GetMapping
    public List<TiendaResponseDTO> listarAprobadas() {
        return tiendaRepository.findByEstado(Tienda.Estado.APPROVED)
                .stream().map(TiendaResponseDTO::desde).collect(Collectors.toList());
    }

    @GetMapping("/por-categoria")
    public List<TiendaResponseDTO> listarPorCategoria(@RequestParam String categoria) {
        Tienda.CategoriaTienda cat = Tienda.CategoriaTienda.valueOf(categoria.toUpperCase());
        return tiendaRepository.findByEstadoAndCategoria(Tienda.Estado.APPROVED, cat)
                .stream().map(TiendaResponseDTO::desde).collect(Collectors.toList());
    }

    // Búsqueda por nombre — usada desde la barra de búsqueda en Inicio
    @GetMapping("/buscar")
    public List<TiendaResponseDTO> buscar(@RequestParam String q) {
        return tiendaRepository.findByEstadoAndNombreContainingIgnoreCase(Tienda.Estado.APPROVED, q)
                .stream().map(TiendaResponseDTO::desde).collect(Collectors.toList());
    }

    @GetMapping("/todas")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<TiendaResponseDTO> listarTodas() {
        return tiendaRepository.findAll()
                .stream().map(TiendaResponseDTO::desde).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public TiendaResponseDTO obtenerPorId(@PathVariable UUID id) {
        Tienda tienda = tiendaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
        return TiendaResponseDTO.desde(tienda);
    }

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
        tienda.setCategoria(Tienda.CategoriaTienda.valueOf(request.categoria().toUpperCase()));

        Tienda guardada = tiendaRepository.save(tienda);

        notificacionClient.enviar(ownerId, "Solicitud de tienda enviada",
                "Tu solicitud para \"" + tienda.getNombre() + "\" está en revisión. Te avisamos apenas la resolvamos.");

        return ResponseEntity.status(HttpStatus.CREATED).body(TiendaResponseDTO.desde(guardada));
    }

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
        if (request.categoria() != null) {
            tienda.setCategoria(Tienda.CategoriaTienda.valueOf(request.categoria().toUpperCase()));
        }

        return ResponseEntity.ok(TiendaResponseDTO.desde(tiendaRepository.save(tienda)));
    }

    @GetMapping("/pendientes")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<TiendaResponseDTO> listarPendientes() {
        return tiendaRepository.findByEstado(Tienda.Estado.PENDING)
                .stream().map(TiendaResponseDTO::desde).collect(Collectors.toList());
    }

    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<TiendaResponseDTO> aprobar(@PathVariable UUID id) {
        Tienda tienda = obtenerPendiente(id);
        tienda.setEstado(Tienda.Estado.APPROVED);
        Tienda guardada = tiendaRepository.save(tienda);

        notificacionClient.enviar(tienda.getOwnerId(), "¡Tienda aprobada!",
                "Tu tienda \"" + tienda.getNombre() + "\" ya está visible en NEXO.");

        return ResponseEntity.ok(TiendaResponseDTO.desde(guardada));
    }

    @PatchMapping("/{id}/rechazar")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<TiendaResponseDTO> rechazar(
            @PathVariable UUID id,
            @Valid @RequestBody RechazoRequestDTO request) {

        Tienda tienda = obtenerPendiente(id);
        tienda.setEstado(Tienda.Estado.REJECTED);
        tienda.setMotivoRechazo(request.motivo());
        Tienda guardada = tiendaRepository.save(tienda);

        notificacionClient.enviar(tienda.getOwnerId(), "Solicitud de tienda rechazada",
                "Motivo: " + request.motivo());

        return ResponseEntity.ok(TiendaResponseDTO.desde(guardada));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        if (!tiendaRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada");
        }
        if (productoClient.tieneProductos(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar: la tienda tiene productos asociados. Eliminalos primero.");
        }
        tiendaRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

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