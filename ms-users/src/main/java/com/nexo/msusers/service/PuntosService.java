package com.nexo.msusers.service;

import com.nexo.msusers.entity.MovimientoPuntos;
import com.nexo.msusers.messaging.EventoPedido;
import com.nexo.msusers.messaging.MensajeInvalidoException;
import com.nexo.msusers.repository.EventoProcesadoRepository;
import com.nexo.msusers.repository.MovimientoPuntosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PuntosService {

    private static final Logger log = LoggerFactory.getLogger(PuntosService.class);

    private final MovimientoPuntosRepository movimientos;
    private final EventoProcesadoRepository procesados;

    @Value("${nexo.puntos.pesos-por-punto:100}")
    private long pesosPorPunto;

    public PuntosService(MovimientoPuntosRepository movimientos, EventoProcesadoRepository procesados) {
        this.movimientos = movimientos;
        this.procesados = procesados;
    }

    @Transactional
    public void procesar(EventoPedido e) {
        if (!"pedido.completado".equals(e.evento())) {
            return;
        }
        if (e.clienteId() == null || e.total() == null) {
            throw new MensajeInvalidoException("pedido.completado sin clienteId o total");
        }
        if (procesados.registrar("puntos:" + e.eventoId()) == 0) {
            log.info("Evento {} ya procesado, se ignora", e.eventoId());
            return;
        }
        if (movimientos.existsByPedidoId(e.pedidoId())) {
            return;
        }

        long puntos = e.total().longValue() / Math.max(1, pesosPorPunto);
        if (puntos <= 0) {
            return;
        }

        MovimientoPuntos m = new MovimientoPuntos();
        m.setUsuarioId(e.clienteId());
        m.setPedidoId(e.pedidoId());
        m.setPuntos(puntos);
        movimientos.save(m);
        log.info("Se acreditaron {} puntos al cliente {} por el pedido {}", puntos, e.clienteId(), e.pedidoId());
    }
}