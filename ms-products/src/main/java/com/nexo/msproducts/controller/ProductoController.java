package com.nexo.msproducts.controller;

import com.nexo.msproducts.client.TiendaClient;
import com.nexo.msproducts.dto.ProductoRequestDTO;
import com.nexo.msproducts.dto.ProductoResponseDTO;
import com.nexo.msproducts.entity.Categoria;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.repository.CategoriaRepository;
import com.nexo.msproducts.repository.ProductoRepository;
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
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final TiendaClient tiendaClient;

    public ProductoController(ProductoRepository productoRepository,
                               CategoriaRepository categoriaRepository,
                               TiendaClient tiendaClient) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.tiendaClient = tiendaClient;
    }

    // E1-H4: público, cualquiera puede ver los productos de una tienda sin cuenta
    @GetMapping
    public List<ProductoResponseDTO> listarPorTienda(@RequestParam UUID tiendaId) {
        return productoRepository.findByTiendaId(tiendaId)
                .stream().map(ProductoResponseDTO::desde).collect(Collectors.toList());
    }

    // E1-H6: público, detalle de un producto
    @GetMapping("/{id}")
    public ProductoResponseDTO obtenerDetalle(@PathVariable UUID id) {
        return ProductoResponseDTO.desde(buscarOFallar(id));
    }

    // E3-H4: publicar un producto nuevo — solo el dueño de la tienda
    @PostMapping
    public ResponseEntity<ProductoResponseDTO> publicar(
            @RequestBody ProductoRequestDTO request,
            JwtAuthenticationToken auth) {

        validarDueno(request.tiendaId(), auth);

        Producto producto = new Producto();
        producto.setTiendaId(request.tiendaId());
        producto.setNombre(request.nombre());
        producto.setDescripcion(request.descripcion());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        producto.setImagenUrl(request.imagenUrl());

        if (request.categoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(request.categoriaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría no encontrada"));
            producto.setCategoria(categoria);
        }

        Producto guardado = productoRepository.save(producto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductoResponseDTO.desde(guardado));
    }

    // E3-H5: editar un producto existente — solo el dueño de la tienda
    @PutMapping("/{id}")
    public ProductoResponseDTO editar(
            @PathVariable UUID id,
            @RequestBody ProductoRequestDTO request,
            JwtAuthenticationToken auth) {

        Producto producto = buscarOFallar(id);
        validarDueno(producto.getTiendaId(), auth);

        producto.setNombre(request.nombre());
        producto.setDescripcion(request.descripcion());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        producto.setImagenUrl(request.imagenUrl());
        return ProductoResponseDTO.desde(productoRepository.save(producto));
    }

    // E3-H6: eliminar un producto — solo el dueño de la tienda
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, JwtAuthenticationToken auth) {
        Producto producto = buscarOFallar(id);
        validarDueno(producto.getTiendaId(), auth);

        productoRepository.delete(producto);
        return ResponseEntity.noContent().build();
    }

    // Usado por ms-orders al crear un pedido: descuenta stock con UPDATE condicional
    @PostMapping("/{id}/reservar-stock")
    public ResponseEntity<Void> reservarStock(@PathVariable UUID id, @RequestParam int cantidad) {
        int filas = productoRepository.reservarStock(id, cantidad);
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente");
        }
        return ResponseEntity.ok().build();
    }

    // Usado por ms-orders al cancelar un pedido: devuelve el stock reservado
    @PostMapping("/{id}/revertir-stock")
    public ResponseEntity<Void> revertirStock(@PathVariable UUID id, @RequestParam int cantidad) {
        productoRepository.revertirStock(id, cantidad);
        return ResponseEntity.ok().build();
    }

    // Confirma, contra ms-stores, que el usuario autenticado es dueño de la tienda
    private void validarDueno(UUID tiendaId, JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        UUID userId = UUID.fromString(jwt.getClaimAsString("sub"));

        UUID ownerId = tiendaClient.obtenerOwnerId(tiendaId);
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No sos el dueño de esta tienda");
        }
    }

    private Producto buscarOFallar(UUID id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    }
}