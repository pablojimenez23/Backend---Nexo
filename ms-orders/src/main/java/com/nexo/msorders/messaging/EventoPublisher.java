package com.nexo.msorders.messaging;

public interface EventoPublisher {
    void publicar(EventoPedido evento);
}