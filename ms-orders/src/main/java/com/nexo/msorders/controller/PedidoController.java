package com.nexo.msorders.controller;

import com.nexo.msorders.client.ConductorClient;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private static final BigDecimal COSTO_ENVIO = new BigDecimal("2000");
    private static final BigDecimal MARGEN_PLATAFORMA_ENVIO = new BigDecimal("800");
    private static final BigDecimal PORCENTAJE_COMISION = new BigDecimal("0.12");
    private static final BigDecimal PORCENTAJE_CARGO_CANCELACION = new BigDecimal("0.20");

    private static final Set<Pedido.Estado> CANCELABLES_SIN_CARGO =
            EnumSet.of(Pedido.Estado.CREATED, Pedido.Estado.PAID);

    private static final Pattern PATRON_HORARIO =
            Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)\\s*-\\s*([01]?\\d|2[0-3]):([0-5]\\d)$");

    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final TiendaClient tiendaClient;
    private final ProductoClient productoClient;
    private final NotificacionClient notificacionClient;
    private final ConductorClient conductorClient;

    @Value("${internal.api.key}")
    private String internalApiKey;

    public PedidoController(PedidoRepository pedidoRepository,
                             ItemPedidoRepository itemPedidoRepository,
                             TiendaClient tiendaClient,
                             ProductoClient productoClient,
                             NotificacionClient notificacionClient,
                             ConductorClient conductorClient) {
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.tiendaClient = tiendaClient;
        this.productoClient = productoClient;
        this.notificacionClient = notificacionClient;
        this.conductorClient = conductorClient;
    }

    @PostMapping
    public ResponseEntity<PedidoResponseDTO> crear(
            @RequestBody CrearPedidoRequestDTO request,
            JwtAuthenticationToken auth) {

        UUID clienteId = obtenerUserId(auth);
        var tienda = tiendaClient.obtenerTienda(request.tiendaId());

        if (!tiendaEstaAbierta(tienda.horario())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La tienda se encuentra cerrada en este momento");
        }

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
        pedido.setGananciaConductor(COSTO_ENVIO.subtract(MARGEN_PLATAFORMA_ENVIO));
        pedido.setTotal(total);
        pedido.setPago(pago);

        Pedido guardado = pedidoRepository.save(pedido);
        items.forEach(item -> item.setPedido(guardado));
        itemPedidoRepository.saveAll(items);

        return ResponseEntity.status(HttpStatus.CREATED).body(PedidoResponseDTO.desde(guardado));
    }

    @PostMapping("/{id}/pagar")
    public PedidoResponseDTO pagar(@PathVariable UUID id, JwtAuthenticationToken auth) {
        Pedido pedido = buscarPropio(id, auth);
        if (pedido.getEstado() != Pedido.Estado.CREATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido no está en estado CREATED");
        }
        pedido.getPago().setEstado(PagoSimulado.Estado.APPROVED);
        pedido.setEstado(Pedido.Estado.PAID);
        Pedido guardado = pedidoRepository.save(pedido);

        notificacionClient.enviar(guardado.getClienteId(), "¡Pago confirmado!",
                "Tu pedido fue pagado con éxito. La tienda lo va a preparar pronto.");

        try {
            var tienda = tiendaClient.obtenerTienda(guardado.getTiendaId());
            notificacionClient.enviar(tienda.ownerId(), "¡Nuevo pedido recibido!",
                    "Tenés un pedido pagado esperando ser tomado.");
        } catch (Exception e) {
            // Si falla el aviso a la tienda, no bloqueamos el pago del cliente
        }

        return PedidoResponseDTO.desde(guardado);
    }

    @PatchMapping("/{id}/cancelar")
    public PedidoResponseDTO cancelar(
            @PathVariable UUID id,
            @Valid @RequestBody CancelarPedidoRequestDTO request,
            JwtAuthenticationToken auth) {

        Pedido pedido = buscarPropio(id, auth);

        if (CANCELABLES_SIN_CARGO.contains(pedido.getEstado())) {
            pedido.getPago().setMontoReembolsado(pedido.getTotal());
        } else if (pedido.getEstado() == Pedido.Estado.READY) {
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

        Pedido guardado = pedidoRepository.save(pedido);

        if (guardado.getConductorId() != null) {
            notificacionClient.enviar(guardado.getConductorId(), "Pedido cancelado",
                    "El pedido que ibas a retirar fue cancelado por el cliente.");
        }

        return PedidoResponseDTO.desde(guardado);
    }

    @PatchMapping("/{id}/tomar-orden")
    public PedidoResponseDTO tomarOrden(@PathVariable UUID id) {
        PedidoResponseDTO resultado = avanzarEstado(id, Pedido.Estado.PAID, Pedido.Estado.CONFIRMED);
        notificacionClient.enviar(resultado.clienteId(), "¡Tu pedido fue tomado!",
                "La tienda ya lo está preparando.");
        return resultado;
    }

    @PatchMapping("/{id}/listo")
    public PedidoResponseDTO marcarListo(@PathVariable UUID id) {
        PedidoResponseDTO resultado = avanzarEstado(id, Pedido.Estado.CONFIRMED, Pedido.Estado.READY);
        notificacionClient.enviar(resultado.clienteId(), "¡Tu pedido está listo!",
                "Un conductor lo va a retirar pronto.");

        // Avisamos a todos los conductores disponibles que hay un pedido nuevo esperando
        conductorClient.obtenerConductoresDisponibles().forEach(conductorSub ->
                notificacionClient.enviar(UUID.fromString(conductorSub), "¡Nuevo pedido disponible!",
                        "Hay un pedido esperando ser retirado cerca tuyo.")
        );

        return resultado;
    }

    @PatchMapping("/{id}/aceptar")
    @Transactional
    public PedidoResponseDTO aceptar(@PathVariable UUID id, JwtAuthenticationToken auth) {
        UUID conductorId = obtenerUserId(auth);
        int filas = pedidoRepository.asignarConductor(id, conductorId);
        if (filas == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido ya fue tomado por otro conductor");
        }

        Pedido pedido = pedidoRepository.findById(id).orElseThrow();

        try {
            var tienda = tiendaClient.obtenerTienda(pedido.getTiendaId());
            notificacionClient.enviar(tienda.ownerId(), "Un conductor viene en camino",
                    "Ya asignamos un conductor para retirar el pedido.");
        } catch (Exception e) {
            // No bloqueamos la aceptación del conductor si falla este aviso
        }

        return PedidoResponseDTO.desde(pedido);
    }

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
                "Confirmá en la app que lo recibiste correctamente.");

        return PedidoResponseDTO.desde(guardado);
    }

    @PatchMapping("/{id}/confirmar-recepcion")
    public PedidoResponseDTO confirmarRecepcion(@PathVariable UUID id, JwtAuthenticationToken auth) {
        Pedido pedido = buscarPropio(id, auth);
        if (pedido.getEstado() != Pedido.Estado.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo podés confirmar la recepción de un pedido ya entregado");
        }
        pedido.setEstado(Pedido.Estado.COMPLETED);
        return PedidoResponseDTO.desde(pedidoRepository.save(pedido));
    }

    @GetMapping("/{id}")
    public PedidoResponseDTO detalle(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return PedidoResponseDTO.desde(buscarPropio(id, auth));
    }

    @GetMapping("/mios")
    public List<PedidoResponseDTO> misPedidos(JwtAuthenticationToken auth) {
        UUID clienteId = obtenerUserId(auth);
        return pedidoRepository.findByClienteId(clienteId)
                .stream().map(PedidoResponseDTO::desde).collect(Collectors.toList());
    }

    @GetMapping("/tienda")
    public List<PedidoResponseDTO> pedidosDeTienda(@RequestParam UUID tiendaId) {
        return pedidoRepository.findByTiendaId(tiendaId)
                .stream().map(PedidoResponseDTO::desde).collect(Collectors.toList());
    }

    @GetMapping("/tienda/completados")
    public List<PedidoResponseDTO> pedidosCompletadosDeTienda(@RequestParam UUID tiendaId) {
        return pedidoRepository.findByTiendaIdAndEstado(tiendaId, Pedido.Estado.COMPLETED)
                .stream().map(PedidoResponseDTO::desde).collect(Collectors.toList());
    }

    @GetMapping("/disponibles")
    public List<PedidoResponseDTO> pedidosDisponibles() {
        return pedidoRepository.findByEstadoAndConductorIdIsNull(Pedido.Estado.READY)
                .stream().map(PedidoResponseDTO::desde).collect(Collectors.toList());
    }

    @GetMapping("/mi-entrega")
    public ResponseEntity<PedidoResponseDTO> miEntregaActual(JwtAuthenticationToken auth) {
        UUID conductorId = obtenerUserId(auth);
        return pedidoRepository.findByConductorIdAndEstado(conductorId, Pedido.Estado.DELIVERING)
                .map(p -> ResponseEntity.ok(PedidoResponseDTO.desde(p)))
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/mis-entregas")
    public List<PedidoResponseDTO> misEntregas(JwtAuthenticationToken auth) {
        UUID conductorId = obtenerUserId(auth);
        return pedidoRepository.findByConductorIdAndEstadoIn(
                conductorId, List.of(Pedido.Estado.DELIVERED, Pedido.Estado.COMPLETED))
                .stream().map(PedidoResponseDTO::desde).collect(Collectors.toList());
    }

    @GetMapping("/{id}/verificar-completado")
    public Map<String, Boolean> verificarCompletado(
            @PathVariable UUID id,
            @RequestParam UUID clienteId,
            @RequestParam(required = false) UUID tiendaId,
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {

        if (key == null || !key.equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido a servicios internos");
        }

        boolean valido = pedidoRepository.findById(id)
                .filter(p -> p.getClienteId().equals(clienteId))
                .filter(p -> p.getEstado() == Pedido.Estado.COMPLETED)
                .filter(p -> tiendaId == null || p.getTiendaId().equals(tiendaId))
                .isPresent();

        return Map.of("valido", valido);
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

    private boolean tiendaEstaAbierta(String horario) {
        if (horario == null || horario.isBlank()) {
            return true;
        }

        Matcher m = PATRON_HORARIO.matcher(horario.trim());
        if (!m.matches()) {
            return true;
        }

        LocalTime inicio = LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        LocalTime fin = LocalTime.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
        LocalTime ahora = LocalTime.now();

        if (fin.isAfter(inicio)) {
            return !ahora.isBefore(inicio) && ahora.isBefore(fin);
        }
        return !ahora.isBefore(inicio) || ahora.isBefore(fin);
    }

    private UUID obtenerUserId(JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        return UUID.fromString(jwt.getClaimAsString("sub"));
    }
}