package com.nexo.msusers.service;

import com.nexo.msusers.entity.Conductor;
import com.nexo.msusers.entity.Notificacion;
import com.nexo.msusers.messaging.EventoPedido;
import com.nexo.msusers.repository.ConductorRepository;
import com.nexo.msusers.repository.EventoProcesadoRepository;
import com.nexo.msusers.repository.NotificacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class NotificacionEventoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionEventoService.class);

    private final NotificacionRepository notificaciones;
    private final ConductorRepository conductores;
    private final EventoProcesadoRepository procesados;

    public NotificacionEventoService(NotificacionRepository notificaciones,
                                     ConductorRepository conductores,
                                     EventoProcesadoRepository procesados) {
        this.notificaciones = notificaciones;
        this.conductores = conductores;
        this.procesados = procesados;
    }

    /** Todo ocurre en una sola transacción: si algo falla, tampoco queda registrado como procesado. */
    @Transactional
    public void procesar(EventoPedido e) {
        if (procesados.registrar("notificaciones:" + e.eventoId()) == 0) {
            log.info("Evento {} ya procesado, se ignora", e.eventoId());
            return;
        }

        switch (e.evento()) {
            case "pedido.pagado" -> {
                notificar(e.clienteId(), "¡Pago confirmado!",
                        "Tu pedido fue pagado con éxito. La tienda lo va a preparar pronto.");
                notificar(e.ownerId(), "¡Nuevo pedido recibido!",
                        "Tenés un pedido pagado esperando ser tomado.");
            }
            case "pedido.confirmado" -> notificar(e.clienteId(), "¡Tu pedido fue tomado!",
                    "La tienda ya lo está preparando.");
            case "pedido.listo" -> {
                notificar(e.clienteId(), "¡Tu pedido está listo!",
                        "Un conductor lo va a retirar pronto.");
                conductores.findByEstado(Conductor.Estado.DISPONIBLE).stream()
                        .map(c -> c.getUsuario().getCognitoSub())
                        .filter(Objects::nonNull)
                        .forEach(sub -> notificar(UUID.fromString(sub), "¡Nuevo pedido disponible!",
                                "Hay un pedido esperando ser retirado cerca tuyo."));
            }
            case "pedido.aceptado" -> notificar(e.ownerId(), "Un conductor viene en camino",
                    "Ya asignamos un conductor para retirar el pedido.");
            case "pedido.entregado" -> notificar(e.clienteId(), "¡Pedido entregado!",
                    "Confirmá en la app que lo recibiste correctamente.");
            case "pedido.cancelado" -> notificar(e.conductorId(), "Pedido cancelado",
                    "El pedido que ibas a retirar fue cancelado por el cliente.");
            default -> log.debug("Evento {} sin notificación asociada", e.evento());
        }
    }

    private void notificar(UUID usuarioId, String titulo, String mensaje) {
        if (usuarioId == null) {
            return;
        }
        Notificacion n = new Notificacion();
        n.setUsuarioId(usuarioId);
        n.setTitulo(titulo);
        n.setMensaje(mensaje);
        notificaciones.save(n);
    }
}