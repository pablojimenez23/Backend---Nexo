package com.nexo.msproducts.messaging;

import com.nexo.msproducts.service.StockService;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

/** Responsabilidad: devolver stock al inventario cuando un pedido se cancela. */
@Component
public class StockConsumer extends ConsumidorSqs {

    private final EventoPedidoLector lector;
    private final StockService servicio;

    public StockConsumer(SqsClient sqs, MensajeriaProperties props,
                         EventoPedidoLector lector, StockService servicio) {
        super(sqs, props.colaStock(), props.consumidoresActivos());
        this.lector = lector;
        this.servicio = servicio;
    }

    @Override
    protected void procesar(String cuerpo) {
        servicio.procesar(lector.leer(cuerpo));
    }
}