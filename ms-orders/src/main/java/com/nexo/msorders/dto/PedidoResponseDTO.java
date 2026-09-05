package com.nexo.msorders.dto;

import com.nexo.msorders.entity.Pedido;
import java.math.BigDecimal;
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
        String motivoCancelacion
) {
    public static PedidoResponseDTO desde(Pedido p) {
        return new PedidoResponseDTO(
                p.getId(), p.getClienteId(), p.getTiendaId(), p.getConductorId(),
                p.getDireccionEnvio(), p.getEstado().name(), p.getSubtotal(), p.getCostoEnvio(),
                p.getComisionPlataforma(), p.getGananciaConductor(), p.getTotal(),
                p.getCargoCancelacion(), p.getMotivoCancelacion()
        );
    }
}