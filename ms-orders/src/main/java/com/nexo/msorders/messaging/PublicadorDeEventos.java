package com.nexo.msorders.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Punto único de publicación de eventos de pedido: envía cada evento al topic SNS (que reparte a las
 * colas) y al stream de Kinesis. Los destinos son independientes: si uno falla, el otro se envía igual.
 * El resto del código sigue inyectando EventoPublisher sin cambios.
 */
@Primary
@Component
public class PublicadorDeEventos implements EventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDeEventos.class);

    private final SnsEventoPublisher sns;
    private final KinesisEventoPublisher kinesis;

    public PublicadorDeEventos(SnsEventoPublisher sns, KinesisEventoPublisher kinesis) {
        this.sns = sns;
        this.kinesis = kinesis;
    }

    @Override
    public void publicar(EventoPedido evento) {
        try {
            sns.publicar(evento);
        } catch (Exception e) {
            log.error("Falló el destino SNS para {}: {}", evento.evento(), e.getMessage());
        }
        try {
            kinesis.publicar(evento);
        } catch (Exception e) {
            log.error("Falló el destino Kinesis para {}: {}", evento.evento(), e.getMessage());
        }
    }
}