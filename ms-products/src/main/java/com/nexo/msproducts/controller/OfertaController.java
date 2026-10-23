package com.nexo.msproducts.controller;

import com.nexo.msproducts.client.TiendaClient;
import com.nexo.msproducts.dto.OfertaRequestDTO;
import com.nexo.msproducts.dto.OfertaResponseDTO;
import com.nexo.msproducts.entity.Oferta;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.repository.OfertaRepository;
import com.nexo.msproducts.repository.ProductoRepository;
import com.nexo.msproducts.service.OfertaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ofertas")
public class OfertaController {

    private final OfertaService ofertaService;
    private final OfertaRepository ofertaRepository;
    private final ProductoRepository productoRepository;
    private final TiendaClient tiendaClient;

    public OfertaController(OfertaService ofertaService,
                            OfertaRepository ofertaRepository,
                            ProductoRepository productoRepository,
                            TiendaClient tiendaClient) {
        this.ofertaService = ofertaService;
        this.ofertaRepository = ofertaRepository;
        this.productoRepository = productoRepository;
        this.tiendaClient = tiendaClient;
    }

    @PostMapping
    public ResponseEntity<OfertaResponseDTO> crear(
            @RequestBody OfertaRequestDTO request, JwtAuthenticationToken auth) {

        if (request.productoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el producto de la oferta");
        }
        Producto producto = productoRepository.findById(request.productoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!esAdmin(auth)) {
            verificarDueno(producto.getTiendaId(), obtenerUserId(auth));
        }

        Oferta oferta = ofertaService.crear(producto, request, Instant.now());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OfertaResponseDTO.desde(oferta, producto.getPrecio()));
    }

    /** Ofertas activas y programadas de la tienda, para su panel. */
    @GetMapping("/mias")
    public List<OfertaResponseDTO> deTienda(@RequestParam UUID tiendaId, JwtAuthenticationToken auth) {
        if (!esAdmin(auth)) {
            verificarDueno(tiendaId, obtenerUserId(auth));
        }

        Map<UUID, BigDecimal> precios = productoRepository.findByTiendaId(tiendaId).stream()
                .collect(Collectors.toMap(Producto::getId, Producto::getPrecio));

        return ofertaService.pendientesDeTienda(tiendaId, Instant.now()).stream()
                .map(o -> OfertaResponseDTO.desde(o, precios.get(o.getProductoId())))
                .collect(Collectors.toList());
    }

    @PatchMapping("/{id}/cancelar")
    public OfertaResponseDTO cancelar(@PathVariable UUID id, JwtAuthenticationToken auth) {
        Oferta oferta = ofertaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta no encontrada"));

        if (!esAdmin(auth)) {
            verificarDueno(oferta.getTiendaId(), obtenerUserId(auth));
        }

        Oferta cancelada = ofertaService.cancelar(oferta);
        BigDecimal precio = productoRepository.findById(oferta.getProductoId())
                .map(Producto::getPrecio)
                .orElse(null);
        return OfertaResponseDTO.desde(cancelada, precio);
    }

    private void verificarDueno(UUID tiendaId, UUID ownerId) {
        UUID duenoReal = tiendaClient.obtenerOwnerId(tiendaId);
        if (duenoReal == null || !duenoReal.equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No sos el dueño de esta tienda");
        }
    }

    private boolean esAdmin(JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        List<String> grupos = jwt.getClaimAsStringList("cognito:groups");
        return grupos != null && grupos.contains("ADMIN");
    }

    private UUID obtenerUserId(JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        return UUID.fromString(jwt.getClaimAsString("sub"));
    }
}