package com.nexo.msusers.messaging;

import com.nexo.msusers.service.PuntosService;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

/** Responsabilidad: sumar puntos de fidelidad cuando un pedido se completa. */
@Component
public class PuntosConsumer extends ConsumidorSqs {

    private final EventoPedidoLector lector;
    private final PuntosService servicio;

    public PuntosConsumer(SqsClient sqs, MensajeriaProperties props,
                          EventoPedidoLector lector, PuntosService servicio) {
        super(sqs, props.colaPuntos(), props.consumidoresActivos());
        this.lector = lector;
        this.servicio = servicio;
    }

    @Override
    protected void procesar(String cuerpo) {
        servicio.procesar(lector.leer(cuerpo));
    }
}