package com.nexo.msproducts.controller;

import com.nexo.msproducts.client.TiendaClient;
import com.nexo.msproducts.dto.ProductoRequestDTO;
import com.nexo.msproducts.dto.ProductoResponseDTO;
import com.nexo.msproducts.dto.ReservaStockResponseDTO;
import com.nexo.msproducts.entity.Oferta;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.entity.VarianteProducto;
import com.nexo.msproducts.repository.ProductoRepository;
import com.nexo.msproducts.repository.VarianteProductoRepository;
import com.nexo.msproducts.service.CatalogoTipos;
import com.nexo.msproducts.service.OfertaService;
import com.nexo.msproducts.service.ProductoDetalleService;
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
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;
    private final TiendaClient tiendaClient;
    private final OfertaService ofertaService;
    private final ProductoDetalleService detalle;
    private final VarianteProductoRepository varianteRepository;

    @Value("${internal.api.key}")
    private String internalApiKey;

    public ProductoController(ProductoRepository productoRepository,
                              TiendaClient tiendaClient,
                              OfertaService ofertaService,
                              ProductoDetalleService detalle,
                              VarianteProductoRepository varianteRepository) {
        this.productoRepository = productoRepository;
        this.tiendaClient = tiendaClient;
        this.ofertaService = ofertaService;
        this.detalle = detalle;
        this.varianteRepository = varianteRepository;
    }

    /** Tipos de producto y los datos que pide cada uno. La app arma el formulario con esto. */
    @GetMapping("/tipos")
    public List<CatalogoTipos.TipoInfo> tipos() {
        return detalle.tipos();
    }

    @GetMapping
    public List<ProductoResponseDTO> listarPorTienda(@RequestParam UUID tiendaId) {
        Map<UUID, Oferta> ofertas = ofertaService.vigentesPorTienda(tiendaId, Instant.now());
        return detalle.responderTodos(productoRepository.findByTiendaId(tiendaId), ofertas);
    }

    @GetMapping("/{id}")
    public ProductoResponseDTO obtenerPorId(@PathVariable UUID id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        Oferta oferta = ofertaService.vigenteDe(id, Instant.now()).orElse(null);
        return detalle.responder(producto, oferta);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ProductoResponseDTO> crear(
            @RequestBody ProductoRequestDTO request, JwtAuthenticationToken auth) {

        UUID ownerId = obtenerUserId(auth);
        // Un admin puede cargar productos en cualquier tienda (útil para poblar datos de prueba)
        if (!esAdmin(auth)) {
            verificarDueno(request.tiendaId(), ownerId);
        }

        Producto producto = new Producto();
        producto.setTiendaId(request.tiendaId());
        detalle.aplicar(producto, request, false);

        Producto guardado = productoRepository.save(producto);
        detalle.guardarHijos(guardado, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(detalle.responder(guardado, null));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ProductoResponseDTO> editar(
            @PathVariable UUID id, @RequestBody ProductoRequestDTO request, JwtAuthenticationToken auth) {

        UUID ownerId = obtenerUserId(auth);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!esAdmin(auth)) {
            verificarDueno(producto.getTiendaId(), ownerId);
        }

        // La app antigua no manda variantes: si el producto ya las tiene, se respetan su precio y su stock
        boolean variantesSinCambios = request.variantes() == null && detalle.tieneVariantes(id);
        detalle.aplicar(producto, request, variantesSinCambios);

        Producto guardado = productoRepository.save(producto);
        detalle.guardarHijos(guardado, request);
        return ResponseEntity.ok(detalle.responder(guardado, null));
    }

    @PatchMapping("/{id}/disponibilidad")
    public ResponseEntity<ProductoResponseDTO> cambiarDisponibilidad(
            @PathVariable UUID id, @RequestParam boolean disponible) {

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        producto.setDisponible(disponible);
        return ResponseEntity.ok(detalle.responder(productoRepository.save(producto), null));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, JwtAuthenticationToken auth) {
        UUID ownerId = obtenerUserId(auth);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!esAdmin(auth)) {
            verificarDueno(producto.getTiendaId(), ownerId);
        }
        detalle.eliminarDe(id);
        productoRepository.delete(producto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/por-tienda/{tiendaId}")
    @Transactional
    public ResponseEntity<Void> eliminarPorTienda(
            @PathVariable UUID tiendaId,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {

        if (key == null || !key.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a servicios internos");
        }

        List<Producto> productos = productoRepository.findByTiendaId(tiendaId);
        detalle.eliminarDeProductos(productos.stream().map(Producto::getId).filter(Objects::nonNull).toList());
        productoRepository.deleteAll(productos);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reserva stock de forma atómica y, si el producto tiene una oferta vigente, consume su cupo.
     * Si el producto tiene variantes (tamaños), varianteId es obligatorio y el stock sale de esa variante.
     * La respuesta dice cuántas unidades se cobran a precio de oferta; el resto va a precio normal.
     */
    @PatchMapping("/{id}/reservar-stock")
    @Transactional
    public ResponseEntity<ReservaStockResponseDTO> reservarStock(
            @PathVariable UUID id, @RequestParam int cantidad,
            @RequestParam(required = false) UUID varianteId,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {

        if (key == null || !key.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a servicios internos");
        }
        if (cantidad <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
        }

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!detalle.tieneVariantes(id)) {
            if (varianteId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este producto no tiene opciones");
            }
            if (productoRepository.reservarStock(id, cantidad) == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente");
            }
            return ResponseEntity.ok(ofertaService.aplicarOferta(producto, cantidad, Instant.now()));
        }

        if (varianteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí una opción para este producto");
        }
        VarianteProducto variante = varianteRepository.findByIdAndProductoId(varianteId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La opción elegida no existe"));

        if (varianteRepository.reservarStock(varianteId, id, cantidad) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente para la opción elegida");
        }
        productoRepository.descontarStockAgregado(id, cantidad);

        return ResponseEntity.ok(ofertaService.aplicarOferta(producto, variante.getPrecio(), cantidad, Instant.now()));
    }

    /**
     * Devuelve stock (a la variante, si corresponde); si la reserva había usado cupo de una oferta,
     * también lo devuelve.
     */
    @PatchMapping("/{id}/revertir-stock")
    @Transactional
    public ResponseEntity<Void> revertirStock(
            @PathVariable UUID id, @RequestParam int cantidad,
            @RequestParam(required = false) UUID ofertaId,
            @RequestParam(defaultValue = "0") int unidadesOferta,
            @RequestParam(required = false) UUID varianteId,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {

        if (key == null || !key.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a servicios internos");
        }

        productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (varianteId == null) {
            productoRepository.revertirStock(id, cantidad);
        } else if (varianteRepository.revertirStock(varianteId, id, cantidad) > 0) {
            // Si la variante ya fue borrada no hay a quién devolverle el stock
            productoRepository.revertirStock(id, cantidad);
        }

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