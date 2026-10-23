package com.nexo.msproducts.service;

import com.nexo.msproducts.dto.OfertaRequestDTO;
import com.nexo.msproducts.dto.ReservaStockResponseDTO;
import com.nexo.msproducts.entity.Oferta;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.repository.OfertaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OfertaService {

    private static final ReservaStockResponseDTO SIN_OFERTA = new ReservaStockResponseDTO(null, null, 0);

    // Reglas de negocio: los valores por defecto se pueden cambiar en application.properties
    @Value("${nexo.ofertas.descuento-minimo:10}")
    private int descuentoMinimo = 10;
    @Value("${nexo.ofertas.descuento-maximo:40}")
    private int descuentoMaximo = 40;
    @Value("${nexo.ofertas.duracion-minima-horas:6}")
    private int duracionMinimaHoras = 6;
    @Value("${nexo.ofertas.duracion-maxima-horas:72}")
    private int duracionMaximaHoras = 72;
    @Value("${nexo.ofertas.espera-horas:24}")
    private int esperaHoras = 24;
    @Value("${nexo.ofertas.maximo-activas-por-tienda:3}")
    private int maximoActivasPorTienda = 3;
    @Value("${nexo.ofertas.precio-minimo:1000}")
    private int precioMinimo = 1000;
    @Value("${nexo.ofertas.cupo-minimo:3}")
    private int cupoMinimo = 3;
    @Value("${nexo.ofertas.cupo-maximo:100}")
    private int cupoMaximo = 100;
    @Value("${nexo.ofertas.limite-por-pedido-defecto:2}")
    private int limitePorPedidoDefecto = 2;
    @Value("${nexo.ofertas.limite-por-pedido-maximo:5}")
    private int limitePorPedidoMaximo = 5;
    @Value("${nexo.ofertas.inicio-maximo-dias:7}")
    private int inicioMaximoDias = 7;

    private final OfertaRepository ofertas;

    public OfertaService(OfertaRepository ofertas) {
        this.ofertas = ofertas;
    }

    @Transactional
    public Oferta crear(Producto producto, OfertaRequestDTO req, Instant ahora) {
        Integer porcentaje = req.porcentaje();
        if (porcentaje == null || porcentaje < descuentoMinimo || porcentaje > descuentoMaximo) {
            throw invalida("El descuento debe estar entre " + descuentoMinimo + " % y " + descuentoMaximo + " %");
        }

        Integer horas = req.duracionHoras();
        if (horas == null || horas < duracionMinimaHoras || horas > duracionMaximaHoras) {
            throw invalida("La oferta debe durar entre " + duracionMinimaHoras + " y "
                    + duracionMaximaHoras + " horas");
        }

        if (producto.getPrecio().compareTo(BigDecimal.valueOf(precioMinimo)) < 0) {
            throw invalida("Solo se pueden hacer ofertas sobre productos de $" + precioMinimo + " o más");
        }

        Instant inicio = req.inicio() == null ? ahora : req.inicio();
        if (inicio.isBefore(ahora.minus(5, ChronoUnit.MINUTES))) {
            throw invalida("La oferta no puede empezar en el pasado");
        }
        if (inicio.isAfter(ahora.plus(inicioMaximoDias, ChronoUnit.DAYS))) {
            throw invalida("La oferta no puede empezar con más de " + inicioMaximoDias + " días de anticipación");
        }
        if (inicio.isBefore(ahora)) {
            inicio = ahora;
        }
        Instant fin = inicio.plus(horas, ChronoUnit.HOURS);

        Integer cupo = req.cupo();
        if (cupo != null) {
            if (cupo < cupoMinimo || cupo > cupoMaximo) {
                throw invalida("El cupo debe estar entre " + cupoMinimo + " y " + cupoMaximo + " unidades");
            }
            if (producto.getStock() != null && cupo > producto.getStock()) {
                throw invalida("El cupo no puede superar el stock disponible (" + producto.getStock() + ")");
            }
        }

        int limite = req.limitePorPedido() == null ? limitePorPedidoDefecto : req.limitePorPedido();
        if (limite < 1 || limite > limitePorPedidoMaximo) {
            throw invalida("El límite por pedido debe estar entre 1 y " + limitePorPedidoMaximo + " unidades");
        }

        if (!ofertas.findByProductoIdAndEstadoAndFinAfter(producto.getId(), Oferta.Estado.ACTIVA, ahora).isEmpty()) {
            throw conflicto("Este producto ya tiene una oferta activa o programada");
        }

        if (ofertas.countByTiendaIdAndEstadoAndFinAfter(producto.getTiendaId(), Oferta.Estado.ACTIVA, ahora)
                >= maximoActivasPorTienda) {
            throw conflicto("Tu tienda ya tiene " + maximoActivasPorTienda + " ofertas activas o programadas");
        }

        Optional<Oferta> ultima = ofertas.findFirstByProductoIdAndEstadoOrderByFinDesc(
                producto.getId(), Oferta.Estado.ACTIVA);
        if (ultima.isPresent() && inicio.isBefore(ultima.get().getFin().plus(esperaHoras, ChronoUnit.HOURS))) {
            throw conflicto("Debe pasar al menos " + esperaHoras
                    + " horas desde que terminó la oferta anterior de este producto");
        }

        Oferta oferta = new Oferta();
        oferta.setProductoId(producto.getId());
        oferta.setTiendaId(producto.getTiendaId());
        oferta.setPorcentaje(porcentaje);
        oferta.setInicio(inicio);
        oferta.setFin(fin);
        oferta.setCupo(cupo);
        oferta.setLimitePorPedido(limite);
        oferta.setEstado(Oferta.Estado.ACTIVA);
        return ofertas.save(oferta);
    }

    @Transactional
    public Oferta cancelar(Oferta oferta) {
        oferta.setEstado(Oferta.Estado.CANCELADA);
        return ofertas.save(oferta);
    }

    /** Oferta vigente del producto (empezó, no terminó y le queda cupo), si la hay. */
    public Optional<Oferta> vigenteDe(UUID productoId, Instant ahora) {
        return ofertas
                .findByProductoIdAndEstadoAndInicioLessThanEqualAndFinAfter(
                        productoId, Oferta.Estado.ACTIVA, ahora, ahora)
                .stream()
                .filter(Oferta::tieneCupo)
                .findFirst();
    }

    /** Ofertas vigentes de una tienda, indexadas por producto. */
    public Map<UUID, Oferta> vigentesPorTienda(UUID tiendaId, Instant ahora) {
        return ofertas
                .findByTiendaIdAndEstadoAndInicioLessThanEqualAndFinAfter(
                        tiendaId, Oferta.Estado.ACTIVA, ahora, ahora)
                .stream()
                .filter(Oferta::tieneCupo)
                .collect(Collectors.toMap(Oferta::getProductoId, o -> o, (a, b) -> a));
    }

    /** Ofertas activas y programadas de una tienda (para su panel). */
    public List<Oferta> pendientesDeTienda(UUID tiendaId, Instant ahora) {
        return ofertas.findByTiendaIdAndEstadoAndFinAfter(tiendaId, Oferta.Estado.ACTIVA, ahora);
    }

    /**
     * Se llama justo después de reservar el stock. Consume cupo de la oferta vigente (hasta el límite
     * por pedido) y dice cuántas unidades se cobran a precio de oferta. El resto va a precio normal.
     */
    @Transactional
    public ReservaStockResponseDTO aplicarOferta(Producto producto, int cantidad, Instant ahora) {
        Optional<Oferta> vigente = vigenteDe(producto.getId(), ahora);
        if (vigente.isEmpty()) {
            return SIN_OFERTA;
        }
        Oferta oferta = vigente.get();

        // Si el cupo restante es menor que lo pedido, se prueba con menos unidades
        for (int unidades = Math.min(cantidad, oferta.getLimitePorPedido()); unidades >= 1; unidades--) {
            if (ofertas.consumirCupo(oferta.getId(), unidades, ahora, Oferta.Estado.ACTIVA) == 1) {
                return new ReservaStockResponseDTO(
                        oferta.getId(), oferta.precioSobre(producto.getPrecio()), unidades);
            }
        }
        return SIN_OFERTA;
    }

    @Transactional
    public void devolverCupo(UUID ofertaId, int unidades) {
        if (ofertaId != null && unidades > 0) {
            ofertas.devolverCupo(ofertaId, unidades);
        }
    }

    private static ResponseStatusException invalida(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private static ResponseStatusException conflicto(String mensaje) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
    }
}