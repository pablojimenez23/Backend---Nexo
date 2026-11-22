package com.nexo.msorders.dto;

import com.nexo.msorders.entity.Pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PedidoResponseDTO(
        UUID id,
        UUID clienteId,
        UUID tiendaId,
        UUID conductorId,
        String direccionEnvio,
        String estado,
        BigDecimal subtotal,
        BigDecimal costoEnvio,
        BigDecimal comisionPlataforma,
        BigDecimal gananciaConductor,
        BigDecimal total,
        BigDecimal cargoCancelacion,
        String motivoCancelacion,
        BigDecimal descuentoPuntos,
        Long puntosCanjeados,
        Instant creadoEn
) {
    /** Constructor sin fecha de creación, para no romper código ni tests existentes. */
    public PedidoResponseDTO(UUID id, UUID clienteId, UUID tiendaId, UUID conductorId,
                             String direccionEnvio, String estado, BigDecimal subtotal,
                             BigDecimal costoEnvio, BigDecimal comisionPlataforma,
                             BigDecimal gananciaConductor, BigDecimal total,
                             BigDecimal cargoCancelacion, String motivoCancelacion,
                             BigDecimal descuentoPuntos, Long puntosCanjeados) {
        this(id, clienteId, tiendaId, conductorId, direccionEnvio, estado, subtotal, costoEnvio,
                comisionPlataforma, gananciaConductor, total, cargoCancelacion, motivoCancelacion,
                descuentoPuntos, puntosCanjeados, null);
    }

    /** Constructor anterior (sin datos de canje ni fecha), para no romper código ni tests existentes. */
    public PedidoResponseDTO(UUID id, UUID clienteId, UUID tiendaId, UUID conductorId,
                             String direccionEnvio, String estado, BigDecimal subtotal,
                             BigDecimal costoEnvio, BigDecimal comisionPlataforma,
                             BigDecimal gananciaConductor, BigDecimal total,
                             BigDecimal cargoCancelacion, String motivoCancelacion) {
        this(id, clienteId, tiendaId, conductorId, direccionEnvio, estado, subtotal, costoEnvio,
                comisionPlataforma, gananciaConductor, total, cargoCancelacion, motivoCancelacion,
                BigDecimal.ZERO, 0L, null);
    }

    public static PedidoResponseDTO desde(Pedido p) {
        return new PedidoResponseDTO(
                p.getId(), p.getClienteId(), p.getTiendaId(), p.getConductorId(),
                p.getDireccionEnvio(), p.getEstado().name(), p.getSubtotal(), p.getCostoEnvio(),
                p.getComisionPlataforma(), p.getGananciaConductor(), p.getTotal(),
                p.getCargoCancelacion(), p.getMotivoCancelacion(),
                p.getDescuentoPuntos() != null ? p.getDescuentoPuntos() : BigDecimal.ZERO,
                p.getPuntosCanjeados() != null ? p.getPuntosCanjeados() : 0L,
                p.getCreadoEn()
        );
    }
}