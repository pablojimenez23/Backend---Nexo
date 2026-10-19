package com.nexo.msorders.messaging;

import com.nexo.msorders.dto.PedidoResponseDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento de dominio que ms-orders publica en SNS.
 * eventoId identifica cada publicación y los consumidores lo usan para ser idempotentes.
 */
public record EventoPedido(
        UUID eventoId,
        String evento,
        UUID pedidoId,
        UUID clienteId,
        UUID tiendaId,
        UUID conductorId,
        UUID ownerId,
        BigDecimal total,
        List<Item> items,
        Instant ocurridoEn) {

    public record Item(UUID productoId, int cantidad) {
    }

    public static EventoPedido de(String evento, PedidoResponseDTO p, UUID ownerId, List<Item> items) {
        return new EventoPedido(UUID.randomUUID(), evento, p.id(), p.clienteId(), p.tiendaId(),
                p.conductorId(), ownerId, p.total(), items == null ? List.of() : items, Instant.now());
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{')
          .append("\"eventoId\":").append(q(eventoId)).append(',')
          .append("\"evento\":\"").append(evento).append("\",")
          .append("\"pedidoId\":").append(q(pedidoId)).append(',')
          .append("\"clienteId\":").append(q(clienteId)).append(',')
          .append("\"tiendaId\":").append(q(tiendaId)).append(',')
          .append("\"conductorId\":").append(q(conductorId)).append(',')
          .append("\"ownerId\":").append(q(ownerId)).append(',')
          .append("\"total\":").append(total == null ? "null" : total.toPlainString()).append(',')
          .append("\"ocurridoEn\":\"").append(ocurridoEn).append("\",")
          .append("\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"productoId\":").append(q(it.productoId()))
              .append(",\"cantidad\":").append(it.cantidad()).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String q(UUID id) {
        return id == null ? "null" : "\"" + id + "\"";
    }
}