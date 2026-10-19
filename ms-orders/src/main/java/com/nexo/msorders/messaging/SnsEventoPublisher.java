package com.nexo.msorders.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

@Component
public class SnsEventoPublisher implements EventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsEventoPublisher.class);

    private final SnsClient sns;
    private final MensajeriaProperties props;
    private volatile String topicArn;

    public SnsEventoPublisher(SnsClient sns, MensajeriaProperties props) {
        this.sns = sns;
        this.props = props;
    }

    @Override
    public void publicar(EventoPedido evento) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(evento);
                }
            });
        } else {
            enviar(evento);
        }
    }

    private void enviar(EventoPedido evento) {
        try {
            sns.publish(PublishRequest.builder()
                    .topicArn(arn())
                    .message(evento.toJson())
                    .messageAttributes(Map.of("evento", MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(evento.evento())
                            .build()))
                    .build());
            log.info("Evento {} publicado (pedido {})", evento.evento(), evento.pedidoId());
        } catch (Exception e) {
            log.error("No se pudo publicar {} del pedido {}: {}",
                    evento.evento(), evento.pedidoId(), e.getMessage());
        }
    }

    private String arn() {
        String actual = topicArn;
        if (actual == null) {
            // createTopic es idempotente: si ya existe devuelve su ARN
            actual = sns.createTopic(b -> b.name(props.topicPedidos())).topicArn();
            topicArn = actual;
        }
        return actual;
    }
}