package com.nexo.msorders.controller;

import com.nexo.msorders.client.NotificacionClient;
import com.nexo.msorders.client.ProductoClient;
import com.nexo.msorders.client.TiendaClient;
import com.nexo.msorders.dto.CancelarPedidoRequestDTO;
import com.nexo.msorders.dto.CrearPedidoRequestDTO;
import com.nexo.msorders.dto.PedidoResponseDTO;
import com.nexo.msorders.entity.ItemPedido;
import com.nexo.msorders.entity.PagoSimulado;
import com.nexo.msorders.entity.Pedido;
import com.nexo.msorders.repository.ItemPedidoRepository;
import com.nexo.msorders.repository.PedidoRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private static final BigDecimal COSTO_ENVIO = new BigDecimal("2000");
    private static final BigDecimal PORCENTAJE_COMISION = new BigDecimal("0.12");
    private static final BigDecimal PORCENTAJE_CARGO_CANCELACION = new BigDecimal("0.20");

    private static final Set<Pedido.Estado> CANCELABLES_SIN_CARGO =
            EnumSet.of(Pedido.Estado.CREATED, Pedido.Estado.PAID, Pedido.Estado.CONFIRMED);

    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final TiendaClient tiendaClient;
    private final ProductoClient productoClient;
    private final NotificacionClient notificacionClient;

    public PedidoController(PedidoRepository pedidoRepository,
                             ItemPedidoRepository itemPedidoRepository,
                             TiendaClient tiendaClient,
                             ProductoClient productoClient,
                             NotificacionClient notificacionClient) {
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.tiendaClient = tiendaClient;
        this.productoClient = productoClient;
        this.notificacionClient = notificacionClient;
    }

    // Crea el pedido: valida tienda (horario/monto mínimo) y reserva stock de cada producto
    @PostMapping
    public ResponseEntity<PedidoResponseDTO> crear(
            @RequestBody CrearPedidoRequestDTO request,
            JwtAuthenticationToken auth) {

        UUID clienteId = obtenerUserId(auth);
        var tienda = tiendaClient.obtenerTienda(request.tiendaId());

        BigDecimal subtotal = BigDecimal.ZERO;
        List<ItemPedido> items = new java.util.ArrayList<>();

        for (var itemReq : request.items()) {
            var producto = productoClient.obtenerProducto(itemReq.productoId());
            productoClient.reservarStock(itemReq.productoId(), itemReq.cantidad());

            ItemPedido item = new ItemPedido();
            item.setProductoId(itemReq.productoId());
            item.setCantidad(itemReq.cantidad());
            item.setPrecioUnitario(producto.precio());
            items.add(item);

            subtotal = subtotal.add(producto.precio().multiply(BigDecimal.valueOf(itemReq.cantidad())));
        }

        if (tienda.montoMinimo() != null && subtotal.compareTo(tienda.montoMinimo()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El pedido no alcanza el monto mínimo de la tienda: " + tienda.montoMinimo());
        }

        BigDecimal comision = subtotal.multiply(PORCENTAJE_COMISION);
        BigDecimal total = subtotal.add(COSTO_ENVIO);

        PagoSimulado pago = new PagoSimulado();
        pago.setProveedor("SIMULADO");
        pago.setMonto(total);
        pago.setEstado(PagoSimulado.Estado.PENDING);

        Pedido pedido = new Pedido();
        pedido.setClienteId(clienteId);
        pedido.setTiendaId(request.tiendaId());
        pedido.setDireccionEnvio(request.direccionEnvio());
        pedido.setSubtotal(subtotal);
        pedido.setCostoEnvio(COSTO_ENVIO);
        pedido.setComisionPlataforma(comision);
        pedido.setTotal(total);
        pedido.setPago(pago);

        Pedido guardado = pedidoRepository.save(pedido);
        items.forEach(item -> item.setPedido(guardado));
        itemPedidoRepository.saveAll(items);

        return ResponseEntity.status(HttpStatus.CREATED).body(PedidoResponseDTO.desde(guardado));
    }

    // Pago simulado: siempre aprueba (es simulado). CREATED -> PAID
    @PostMapping("/{id}/pagar")
    public PedidoResponseDTO pagar(@PathVariable UUID id, JwtAuthenticationToken auth) {
        Pedido pedido = buscarPropio(id, auth);
        if (pedido.getEstado() != Pedido.Estado.CREATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido no está en estado CREATED");
        }
        pedido.getPago().setEstado(PagoSimulado.Estado.APPROVED);
        pedido.setEstado(Pedido.Estado.PAID);
        return PedidoResponseDTO.desde(pedidoRepository.save(pedido));
    }

    // Cancelación con la regla de reembolso según el estado actual
    @PatchMapping("/{id}/cancelar")
    public PedidoResponseDTO cancelar(
            @PathVariable UUID id,
            @Valid @RequestBody CancelarPedidoRequestDTO request,
            JwtAuthenticationToken auth) {

        Pedido pedido = buscarPropio(id, auth);

        if (CANCELABLES_SIN_CARGO.contains(pedido.getEstado())) {
            pedido.getPago().setMontoReembolsado(pedido.getTotal());
        } else if (pedido.getEstado() == Pedido.Estado.PREPARING) {
            BigDecimal cargo = pedido.getSubtotal().multiply(PORCENTAJE_CARGO_CANCELACION);
            pedido.setCargoCancelacion(cargo);
            pedido.getPago().setMontoReembolsado(pedido.getTotal().subtract(cargo));
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El pedido ya no admite cancelación en su estado actual");
        }

        itemPedidoRepository.findByPedidoId(pedido.getId())
                .forEach(item -> productoClient.revertirStock(item.getProductoId(), item.getCantidad()));

        pedido.getPago().setEstado(PagoSimulado.Estado.REFUNDED);
        pedido.setEstado(Pedido.Estado.CANCELLED);
        pedido.setMotivoCancelacion(request.motivo());

        return PedidoResponseDTO.desde(pedidoRepository.save(pedido));
    }

    // Avance del flujo de la tienda: CONFIRMED, PREPARING, READY
    @PatchMapping("/{id}/confirmar")
    public PedidoResponseDTO confirmar(@PathVariable UUID id) {
        return avanzarEstado(id, Pedido.Estado.PAID, Pedido.Estado.CONFIRMED);
    }

    @PatchMapping("/{id}/preparar")
    public PedidoResponseDTO preparar(@PathVariable UUID id) {
        return avanzarEstado(id, Pedido.Estado.CONFIRMED, Pedido.Estado.PREPARING);
    }

    @PatchMapping("/{id}/listo")
    public PedidoResponseDTO marcarListo(@PathVariable UUID id) {
        PedidoResponseDTO resultado = avanzarEstado(id, Pedido.Estado.PREPARING, Pedido.Estado.READY);
        notificacionClient.enviar(resultado.clienteId(), "¡Tu pedido está listo!",
                "Un conductor lo va a retirar pronto.");
        return resultado;
    }

    // El conductor acepta el pedido — UPDATE condicional, evita doble asignación
    @PatchMapping("/{id}/aceptar")
    public PedidoResponseDTO aceptar(@PathVariable UUID id, JwtAuthenticationToken auth) {
        UUID conductorId = obtenerUserId(auth);
        int filas = pedidoRepository.asignarConductor(id, conductorId);
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya fue tomado por otro conductor");
        }
        return PedidoResponseDTO.desde(pedidoRepository.findById(id).orElseThrow());
    }

    // Solo el conductor asignado puede marcar la entrega
    @PatchMapping("/{id}/entregado")
    public PedidoResponseDTO entregar(@PathVariable UUID id, JwtAuthenticationToken auth) {
        UUID conductorId = obtenerUserId(auth);
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));

        if (pedido.getEstado() != Pedido.Estado.DELIVERING || !conductorId.equals(pedido.getConductorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No podés marcar este pedido como entregado");
        }
        pedido.setEstado(Pedido.Estado.DELIVERED);
        Pedido guardado = pedidoRepository.save(pedido);

        notificacionClient.enviar(guardado.getClienteId(), "¡Pedido entregado!",
                "Esperamos que lo disfrutes. Gracias por usar NEXO.");

        return PedidoResponseDTO.desde(guardado);
    }

    @GetMapping("/{id}")
    public PedidoResponseDTO detalle(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return PedidoResponseDTO.desde(buscarPropio(id, auth));
    }

    // E2-H7: el cliente ve su propio historial de pedidos
    @GetMapping("/mios")
    public List<PedidoResponseDTO> misPedidos(JwtAuthenticationToken auth) {
        UUID clienteId = obtenerUserId(auth);
        return pedidoRepository.findByClienteId(clienteId)
                .stream().map(PedidoResponseDTO::desde).collect(Collectors.toList());
    }

    // E3-H9: la tienda ve los pedidos que recibió
    @GetMapping("/tienda")
    public List<PedidoResponseDTO> pedidosDeTienda(@RequestParam UUID tiendaId) {
        return pedidoRepository.findByTiendaId(tiendaId)
                .stream().map(PedidoResponseDTO::desde).collect(Collectors.toList());
    }

    // Pedidos READY esperando que un conductor los tome
    @GetMapping("/disponibles")
    public List<PedidoResponseDTO> pedidosDisponibles() {
        return pedidoRepository.findByEstadoAndConductorIdIsNull(Pedido.Estado.READY)
                .stream().map(PedidoResponseDTO::desde).collect(Collectors.toList());
    }

    // El pedido que el conductor tiene asignado y en curso ahora mismo
    @GetMapping("/mi-entrega")
    public ResponseEntity<PedidoResponseDTO> miEntregaActual(JwtAuthenticationToken auth) {
        UUID conductorId = obtenerUserId(auth);
        return pedidoRepository.findByConductorIdAndEstado(conductorId, Pedido.Estado.DELIVERING)
                .map(p -> ResponseEntity.ok(PedidoResponseDTO.desde(p)))
                .orElse(ResponseEntity.noContent().build());
    }

    private PedidoResponseDTO avanzarEstado(UUID id, Pedido.Estado desde, Pedido.Estado hacia) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (pedido.getEstado() != desde) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transición inválida: el pedido está en " + pedido.getEstado());
        }
        pedido.setEstado(hacia);
        return PedidoResponseDTO.desde(pedidoRepository.save(pedido));
    }

    private Pedido buscarPropio(UUID id, JwtAuthenticationToken auth) {
        UUID clienteId = obtenerUserId(auth);
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (!clienteId.equals(pedido.getClienteId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenés acceso a este pedido");
        }
        return pedido;
    }

    private UUID obtenerUserId(JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        return UUID.fromString(jwt.getClaimAsString("sub"));
    }
}