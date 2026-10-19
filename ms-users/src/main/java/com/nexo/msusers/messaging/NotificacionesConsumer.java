package com.nexo.msusers.messaging;

import com.nexo.msusers.service.NotificacionEventoService;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

/** Responsabilidad: convertir eventos de pedido en notificaciones para los usuarios. */
@Component
public class NotificacionesConsumer extends ConsumidorSqs {

    private final EventoPedidoLector lector;
    private final NotificacionEventoService servicio;

    public NotificacionesConsumer(SqsClient sqs, MensajeriaProperties props,
                                  EventoPedidoLector lector, NotificacionEventoService servicio) {
        super(sqs, props.colaNotificaciones(), props.consumidoresActivos());
        this.lector = lector;
        this.servicio = servicio;
    }

    @Override
    protected void procesar(String cuerpo) {
        servicio.procesar(lector.leer(cuerpo));
    }
}