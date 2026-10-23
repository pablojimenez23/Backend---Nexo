package com.nexo.msproducts.controller;

import com.nexo.msproducts.client.TiendaClient;
import com.nexo.msproducts.dto.ProductoRequestDTO;
import com.nexo.msproducts.dto.ProductoResponseDTO;
import com.nexo.msproducts.dto.ReservaStockResponseDTO;
import com.nexo.msproducts.entity.Oferta;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.repository.ProductoRepository;
import com.nexo.msproducts.service.OfertaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;
    private final TiendaClient tiendaClient;
    private final OfertaService ofertaService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    public ProductoController(ProductoRepository productoRepository,
                              TiendaClient tiendaClient,
                              OfertaService ofertaService) {
        this.productoRepository = productoRepository;
        this.tiendaClient = tiendaClient;
        this.ofertaService = ofertaService;
    }

    @GetMapping
    public List<ProductoResponseDTO> listarPorTienda(@RequestParam UUID tiendaId) {
        Map<UUID, Oferta> ofertas = ofertaService.vigentesPorTienda(tiendaId, Instant.now());
        return productoRepository.findByTiendaId(tiendaId)
                .stream()
                .map(p -> ProductoResponseDTO.desde(p, ofertas.get(p.getId())))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ProductoResponseDTO obtenerPorId(@PathVariable UUID id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        Oferta oferta = ofertaService.vigenteDe(id, Instant.now()).orElse(null);
        return ProductoResponseDTO.desde(producto, oferta);
    }

    @PostMapping
    public ResponseEntity<ProductoResponseDTO> crear(
            @RequestBody ProductoRequestDTO request, JwtAuthenticationToken auth) {

        UUID ownerId = obtenerUserId(auth);
        // Un admin puede cargar productos en cualquier tienda (útil para poblar datos de prueba)
        if (!esAdmin(auth)) {
            verificarDueno(request.tiendaId(), ownerId);
        }

        Producto producto = new Producto();
        producto.setTiendaId(request.tiendaId());
        producto.setNombre(request.nombre());
        producto.setDescripcion(request.descripcion());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock() != null ? request.stock() : 0);
        producto.setDisponible(request.disponible() != null ? request.disponible() : true);

        Producto guardado = productoRepository.save(producto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductoResponseDTO.desde(guardado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> editar(
            @PathVariable UUID id, @RequestBody ProductoRequestDTO request, JwtAuthenticationToken auth) {

        UUID ownerId = obtenerUserId(auth);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!esAdmin(auth)) {
            verificarDueno(producto.getTiendaId(), ownerId);
        }

        producto.setNombre(request.nombre());
        producto.setDescripcion(request.descripcion());
        producto.setPrecio(request.precio());
        if (request.stock() != null) {
            producto.setStock(request.stock());
        }
        if (request.disponible() != null) {
            producto.setDisponible(request.disponible());
        }

        return ResponseEntity.ok(ProductoResponseDTO.desde(productoRepository.save(producto)));
    }

    @PatchMapping("/{id}/disponibilidad")
    public ResponseEntity<ProductoResponseDTO> cambiarDisponibilidad(
            @PathVariable UUID id, @RequestParam boolean disponible) {

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        producto.setDisponible(disponible);
        return ResponseEntity.ok(ProductoResponseDTO.desde(productoRepository.save(producto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, JwtAuthenticationToken auth) {
        UUID ownerId = obtenerUserId(auth);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!esAdmin(auth)) {
            verificarDueno(producto.getTiendaId(), ownerId);
        }
        productoRepository.delete(producto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/por-tienda/{tiendaId}")
    public ResponseEntity<Void> eliminarPorTienda(
            @PathVariable UUID tiendaId,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {

        if (key == null || !key.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a servicios internos");
        }

        List<Producto> productos = productoRepository.findByTiendaId(tiendaId);
        productoRepository.deleteAll(productos);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reserva stock de forma atómica y, si el producto tiene una oferta vigente, consume su cupo.
     * La respuesta dice cuántas unidades se cobran a precio de oferta; el resto va a precio normal.
     */
    @PatchMapping("/{id}/reservar-stock")
    @Transactional
    public ResponseEntity<ReservaStockResponseDTO> reservarStock(
            @PathVariable UUID id, @RequestParam int cantidad,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {

        if (key == null || !key.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a servicios internos");
        }
        if (cantidad <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
        }

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (productoRepository.reservarStock(id, cantidad) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente");
        }

        return ResponseEntity.ok(ofertaService.aplicarOferta(producto, cantidad, Instant.now()));
    }

    /**
     * Devuelve stock; si la reserva había usado cupo de una oferta, también lo devuelve.
     */
    @PatchMapping("/{id}/revertir-stock")
    @Transactional
    public ResponseEntity<Void> revertirStock(
            @PathVariable UUID id, @RequestParam int cantidad,
            @RequestParam(required = false) UUID ofertaId,
            @RequestParam(defaultValue = "0") int unidadesOferta,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {

        if (key == null || !key.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a servicios internos");
        }

        productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        productoRepository.revertirStock(id, cantidad);
        ofertaService.devolverCupo(ofertaId, unidadesOferta);
        return ResponseEntity.ok().build();
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