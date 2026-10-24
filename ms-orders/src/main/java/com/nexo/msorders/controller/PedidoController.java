package com.nexo.msorders.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.msorders.client.ProductoClient;
import com.nexo.msorders.client.PuntosClient;
import com.nexo.msorders.client.TiendaClient;
import com.nexo.msorders.dto.CancelarPedidoRequestDTO;
import com.nexo.msorders.dto.CrearPedidoRequestDTO;
import com.nexo.msorders.dto.PedidoResponseDTO;
import com.nexo.msorders.entity.ItemPedido;
import com.nexo.msorders.entity.PagoSimulado;
import com.nexo.msorders.entity.Pedido;
import com.nexo.msorders.messaging.EventoPedido;
import com.nexo.msorders.messaging.EventoPublisher;
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
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    private static final ZoneId ZONA_CHILE = ZoneId.of("America/Santiago");

    private static final Pattern PATRON_HORARIO =
            Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)\\s*-\\s*([01]?\\d|2[0-3]):([0-5]\\d)$");

    private static final int MAX_NOTA = 140;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final TiendaClient tiendaClient;
    private final ProductoClient productoClient;
    private final EventoPublisher eventos;
    private final PuntosClient puntosClient;

    @Value("${internal.api.key}")
    private String internalApiKey;

    /** Cuántos pesos vale un punto al pagar productos con puntos (1 punto = $10). */
    @Value("${nexo.puntos.pesos-por-punto-canje:10}")
    private BigDecimal pesosPorPuntoCanje;

    /** Máximo del subtotal de productos que se puede pagar con puntos en un pedido (en %). */
    @Value("${nexo.puntos.tope-canje-porcentaje:10}")
    private BigDecimal topeCanjePorcentaje;

    /** Mínimo de puntos para poder pagar con puntos en un pedido. */
    @Value("${nexo.puntos.minimo-canje:50}")
    private long minimoCanje;

    public PedidoController(PedidoRepository pedidoRepository,
                             ItemPedidoRepository itemPedidoRepository,
                             TiendaClient tiendaClient,
                             ProductoClient productoClient,
                             EventoPublisher eventos,
                             PuntosClient puntosClient) {
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.tiendaClient = tiendaClient;
        this.productoClient = productoClient;
        this.eventos = eventos;
        this.puntosClient = puntosClient;
    }

    /** Un ítem del pedido ya validado: producto, tamaño elegido, opciones y nota. */
    private record Linea(ProductoClient.ProductoDTO producto,
                         ProductoClient.VarianteDTO variante,
                         List<Elegida> opciones,
                         String nota,
                         int cantidad) {
    }

    /** Una opción elegida junto al nombre de su grupo ("Masa" -> "Delgada"). */
    private record Elegida(String grupo, ProductoClient.OpcionDTO opcion) {
    }

    /** Lo que se reservó en ms-products, para poder devolverlo si el pedido no se concreta. */
    private record Reserva(UUID productoId, UUID varianteId, int cantidad, UUID ofertaId, int unidadesOferta) {
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

        long puntosACanjear = request.puntosACanjear() == null ? 0L : request.puntosACanjear();
        if (puntosACanjear < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los puntos a canjear no pueden ser negativos");
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El pedido no tiene productos");
        }

        // Primero se valida todo (tamaño, opciones, tienda, edad) y recién después se reserva stock
        List<Linea> lineas = new ArrayList<>();
        for (var itemReq : request.items()) {
            lineas.add(resolverLinea(itemReq, request.tiendaId()));
        }

        boolean llevaAlcohol = lineas.stream()
                .anyMatch(l -> Boolean.TRUE.equals(l.producto().requiereMayorEdad()));
        if (llevaAlcohol && !Boolean.TRUE.equals(request.confirmaMayorEdad())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para comprar alcohol tenés que confirmar que sos mayor de 18 años");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<ItemPedido> items = new ArrayList<>();
        List<Reserva> reservas = new ArrayList<>();

        try {
            for (Linea linea : lineas) {
                UUID varianteId = linea.variante() == null ? null : linea.variante().id();
                var reserva = productoClient.reservarStock(linea.producto().id(), varianteId, linea.cantidad());

                int enOferta = unidadesEnOferta(reserva, linea.cantidad());
                reservas.add(new Reserva(linea.producto().id(), varianteId, linea.cantidad(),
                        enOferta > 0 ? reserva.ofertaId() : null, enOferta));

                BigDecimal precioBase = linea.variante() != null
                        ? linea.variante().precio()
                        : linea.producto().precio();
                BigDecimal opcionesUnitario = linea.opciones().stream()
                        .map(e -> e.opcion().precioAdicional() == null ? BigDecimal.ZERO : e.opcion().precioAdicional())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (enOferta > 0) {
                    BigDecimal unitario = reserva.precioOferta().add(opcionesUnitario);
                    items.add(crearItem(linea, unitario, opcionesUnitario, enOferta));
                    subtotal = subtotal.add(unitario.multiply(BigDecimal.valueOf(enOferta)));
                }

                int resto = linea.cantidad() - enOferta;
                if (resto > 0) {
                    BigDecimal unitario = precioBase.add(opcionesUnitario);
                    items.add(crearItem(linea, unitario, opcionesUnitario, resto));
                    subtotal = subtotal.add(unitario.multiply(BigDecimal.valueOf(resto)));
                }
            }
        } catch (RuntimeException e) {
            devolverReservas(reservas);
            throw e;
        }

        if (tienda.montoMinimo() != null && subtotal.compareTo(tienda.montoMinimo()) < 0) {
            devolverReservas(reservas);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El pedido no alcanza el monto mínimo de la tienda: " + tienda.montoMinimo());
        }

        BigDecimal descuentoPuntos = BigDecimal.ZERO;
        if (puntosACanjear > 0) {
            descuentoPuntos = BigDecimal.valueOf(puntosACanjear).multiply(pesosPorPuntoCanje);
            BigDecimal tope = subtotal.multiply(topeCanjePorcentaje)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
                    .min(subtotal);

            if (puntosACanjear < minimoCanje) {
                devolverReservas(reservas);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El mínimo para pagar con puntos es de " + minimoCanje + " puntos");
            }
            if (descuentoPuntos.compareTo(tope) > 0) {
                long maximoPuntos = tope.divide(pesosPorPuntoCanje, 0, RoundingMode.FLOOR).longValue();
                devolverReservas(reservas);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Solo podés pagar con puntos hasta el "
                                + topeCanjePorcentaje.stripTrailingZeros().toPlainString()
                                + " % del subtotal ($" + tope + ", " + maximoPuntos + " puntos)");
            }
        }

        BigDecimal comision = subtotal.multiply(PORCENTAJE_COMISION);
        BigDecimal total = subtotal.add(COSTO_ENVIO).subtract(descuentoPuntos);

        UUID canjeId = null;
        if (puntosACanjear > 0) {
            try {
                canjeId = puntosClient.canjear(clienteId, puntosACanjear);
            } catch (RuntimeException e) {
                devolverReservas(reservas);
                throw e;
            }
        }

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
        if (canjeId != null) {
            pedido.setDescuentoPuntos(descuentoPuntos);
            pedido.setPuntosCanjeados(puntosACanjear);
            pedido.setCanjeId(canjeId);
        }

        Pedido guardado;
        try {
            guardado = pedidoRepository.save(pedido);
            Pedido pedidoGuardado = guardado;
            items.forEach(item -> item.setPedido(pedidoGuardado));
            itemPedidoRepository.saveAll(items);
        } catch (RuntimeException e) {
            if (canjeId != null) {
                puntosClient.revertir(canjeId);
            }
            devolverReservas(reservas);
            throw e;
        }

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

        PedidoResponseDTO dto = PedidoResponseDTO.desde(guardado);
        eventos.publicar(EventoPedido.de("pedido.pagado", dto, ownerDe(guardado.getTiendaId()), List.of()));
        return dto;
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
            pedido.getPago().setMontoReembolsado(pedido.getTotal().subtract(cargo).max(BigDecimal.ZERO));
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El pedido ya no admite cancelación en su estado actual");
        }

        List<EventoPedido.Item> items = itemPedidoRepository.findByPedidoId(pedido.getId()).stream()
                .map(i -> new EventoPedido.Item(i.getProductoId(), i.getCantidad(), i.getVarianteId()))
                .collect(Collectors.toList());

        pedido.getPago().setEstado(PagoSimulado.Estado.REFUNDED);
        pedido.setEstado(Pedido.Estado.CANCELLED);
        pedido.setMotivoCancelacion(request.motivo());

        Pedido guardado = pedidoRepository.save(pedido);

        if (guardado.getCanjeId() != null) {
            puntosClient.revertir(guardado.getCanjeId());
        }

        PedidoResponseDTO dto = PedidoResponseDTO.desde(guardado);
        eventos.publicar(EventoPedido.de("pedido.cancelado", dto, null, items));
        return dto;
    }

    @PatchMapping("/{id}/tomar-orden")
    public PedidoResponseDTO tomarOrden(@PathVariable UUID id) {
        PedidoResponseDTO resultado = avanzarEstado(id, Pedido.Estado.PAID, Pedido.Estado.CONFIRMED);
        eventos.publicar(EventoPedido.de("pedido.confirmado", resultado, null, List.of()));
        return resultado;
    }

    @PatchMapping("/{id}/listo")
    public PedidoResponseDTO marcarListo(@PathVariable UUID id) {
        PedidoResponseDTO resultado = avanzarEstado(id, Pedido.Estado.CONFIRMED, Pedido.Estado.READY);
        eventos.publicar(EventoPedido.de("pedido.listo", resultado, null, List.of()));
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

        PedidoResponseDTO dto = PedidoResponseDTO.desde(pedido);
        eventos.publicar(EventoPedido.de("pedido.aceptado", dto, ownerDe(pedido.getTiendaId()), List.of()));
        return dto;
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

        PedidoResponseDTO dto = PedidoResponseDTO.desde(guardado);
        eventos.publicar(EventoPedido.de("pedido.entregado", dto, null, List.of()));
        return dto;
    }

    @PatchMapping("/{id}/confirmar-recepcion")
    public PedidoResponseDTO confirmarRecepcion(@PathVariable UUID id, JwtAuthenticationToken auth) {
        Pedido pedido = buscarPropio(id, auth);
        if (pedido.getEstado() != Pedido.Estado.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo podés confirmar la recepción de un pedido ya entregado");
        }
        pedido.setEstado(Pedido.Estado.COMPLETED);

        PedidoResponseDTO dto = PedidoResponseDTO.desde(pedidoRepository.save(pedido));
        eventos.publicar(EventoPedido.de("pedido.completado", dto, null, List.of()));
        return dto;
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

    // ==================== helpers de creación ====================

    /** Valida un ítem del pedido contra el producto real: tienda, disponibilidad, tamaño, opciones y nota. */
    private Linea resolverLinea(CrearPedidoRequestDTO.ItemRequest itemReq, UUID tiendaId) {
        if (itemReq.productoId() == null || itemReq.cantidad() == null || itemReq.cantidad() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad de cada producto debe ser mayor a cero");
        }

        var producto = productoClient.obtenerProducto(itemReq.productoId());

        if (producto.tiendaId() != null && !producto.tiendaId().equals(tiendaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un producto del pedido no pertenece a esta tienda");
        }
        if (Boolean.FALSE.equals(producto.disponible())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "\"" + producto.nombre() + "\" no está disponible en este momento");
        }

        List<ProductoClient.VarianteDTO> variantes = producto.variantes() == null ? List.of() : producto.variantes();
        ProductoClient.VarianteDTO variante = null;
        if (!variantes.isEmpty()) {
            if (itemReq.varianteId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Elegí una opción de \"" + producto.nombre() + "\"");
            }
            variante = variantes.stream()
                    .filter(v -> itemReq.varianteId().equals(v.id()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La opción elegida no existe en \"" + producto.nombre() + "\""));
            if (Boolean.FALSE.equals(variante.disponible())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La opción \"" + variante.nombre() + "\" no está disponible en este momento");
            }
        } else if (itemReq.varianteId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "\"" + producto.nombre() + "\" no tiene opciones para elegir");
        }

        List<Elegida> elegidas = validarOpciones(producto, itemReq.opcionesIds());
        String nota = validarNota(itemReq.nota());

        return new Linea(producto, variante, elegidas, nota, itemReq.cantidad());
    }

    /**
     * Revisa las opciones elegidas contra los grupos del producto: que existan, que estén disponibles
     * y que en cada grupo se respete el mínimo (obligatorio) y el máximo.
     */
    private List<Elegida> validarOpciones(ProductoClient.ProductoDTO producto, List<UUID> opcionesIds) {
        List<ProductoClient.GrupoDTO> grupos = producto.grupos() == null ? List.of() : producto.grupos();
        Set<UUID> pedidas = opcionesIds == null ? Set.of() : new LinkedHashSet<>(opcionesIds);

        Map<UUID, Elegida> encontradas = new LinkedHashMap<>();
        Map<UUID, Integer> porGrupo = new LinkedHashMap<>();
        for (UUID opcionId : pedidas) {
            Elegida elegida = null;
            ProductoClient.GrupoDTO grupoDeOpcion = null;
            for (ProductoClient.GrupoDTO grupo : grupos) {
                List<ProductoClient.OpcionDTO> lista = grupo.opciones() == null ? List.of() : grupo.opciones();
                for (ProductoClient.OpcionDTO opcion : lista) {
                    if (opcionId != null && opcionId.equals(opcion.id())) {
                        elegida = new Elegida(grupo.nombre(), opcion);
                        grupoDeOpcion = grupo;
                    }
                }
            }
            if (elegida == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Una de las opciones elegidas no existe en \"" + producto.nombre() + "\"");
            }
            if (Boolean.FALSE.equals(elegida.opcion().disponible())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La opción \"" + elegida.opcion().nombre() + "\" no está disponible en este momento");
            }
            encontradas.put(opcionId, elegida);
            porGrupo.merge(grupoDeOpcion.id(), 1, Integer::sum);
        }

        for (ProductoClient.GrupoDTO grupo : grupos) {
            int elegidasEnGrupo = porGrupo.getOrDefault(grupo.id(), 0);
            int minimo = grupo.minimo() == null ? 0 : grupo.minimo();
            int maximo = grupo.maximo() == null ? Integer.MAX_VALUE : grupo.maximo();
            if (elegidasEnGrupo < minimo) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        minimo == 1
                                ? "Elegí una opción en \"" + grupo.nombre() + "\""
                                : "Elegí al menos " + minimo + " opciones en \"" + grupo.nombre() + "\"");
            }
            if (elegidasEnGrupo > maximo) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "En \"" + grupo.nombre() + "\" podés elegir hasta " + maximo);
            }
        }

        // Se guardan en el orden de los grupos del producto, no en el orden en que llegaron
        List<Elegida> ordenadas = new ArrayList<>();
        for (ProductoClient.GrupoDTO grupo : grupos) {
            for (Elegida e : encontradas.values()) {
                if (grupo.nombre() != null && grupo.nombre().equals(e.grupo())) {
                    ordenadas.add(e);
                }
            }
        }
        return ordenadas;
    }

    private static String validarNota(String nota) {
        if (nota == null || nota.isBlank()) {
            return null;
        }
        String limpia = nota.trim();
        if (limpia.length() > MAX_NOTA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nota puede tener hasta " + MAX_NOTA + " caracteres");
        }
        return limpia;
    }

    /** Cuántas unidades de la reserva se cobran a precio de oferta (0 si no hay oferta). */
    private static int unidadesEnOferta(ProductoClient.ReservaDTO reserva, int cantidad) {
        if (reserva == null || reserva.ofertaId() == null
                || reserva.precioOferta() == null || reserva.unidadesEnOferta() == null) {
            return 0;
        }
        return Math.min(Math.max(reserva.unidadesEnOferta(), 0), cantidad);
    }

    private ItemPedido crearItem(Linea linea, BigDecimal precioUnitario, BigDecimal opcionesUnitario, int cantidad) {
        ItemPedido item = new ItemPedido();
        item.setProductoId(linea.producto().id());
        item.setNombreProducto(linea.producto().nombre());
        if (linea.variante() != null) {
            item.setVarianteId(linea.variante().id());
            item.setVarianteNombre(linea.variante().nombre());
        }
        if (!linea.opciones().isEmpty()) {
            item.setOpcionesDetalle(opcionesComoJson(linea.opciones()));
            item.setOpcionesUnitario(opcionesUnitario);
        }
        item.setNota(linea.nota());
        item.setCantidad(cantidad);
        item.setPrecioUnitario(precioUnitario);
        return item;
    }

    private static String opcionesComoJson(List<Elegida> opciones) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Elegida elegida : opciones) {
            Map<String, Object> dato = new LinkedHashMap<>();
            dato.put("grupo", elegida.grupo());
            dato.put("nombre", elegida.opcion().nombre());
            dato.put("precio", elegida.opcion().precioAdicional() == null
                    ? BigDecimal.ZERO : elegida.opcion().precioAdicional());
            lista.add(dato);
        }
        try {
            return MAPPER.writeValueAsString(lista);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudieron guardar las opciones");
        }
    }

    /** Devuelve el stock (y el cupo de oferta) reservado cuando el pedido se rechaza antes de crearse. */
    private void devolverReservas(List<Reserva> reservas) {
        reservas.forEach(r -> productoClient.revertirStock(
                r.productoId(), r.varianteId(), r.cantidad(), r.ofertaId(), r.unidadesOferta()));
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

    /** Dueño de la tienda; si ms-stores no responde devuelve null y el evento sale igual. */
    private UUID ownerDe(UUID tiendaId) {
        try {
            return tiendaClient.obtenerTienda(tiendaId).ownerId();
        } catch (Exception e) {
            return null;
        }
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
        LocalTime ahora = LocalTime.now(ZONA_CHILE);

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