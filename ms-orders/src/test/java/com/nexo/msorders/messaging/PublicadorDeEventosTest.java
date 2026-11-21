package com.nexo.msorders.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicadorDeEventosTest {

    @Mock private SnsEventoPublisher sns;
    @Mock private KinesisEventoPublisher kinesis;

    private EventoPedido evento() {
        return new EventoPedido(UUID.randomUUID(), "pedido.completado", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, null, new BigDecimal("5000"), List.of(), Instant.now());
    }

    @Test
    void publicaEnSnsYEnKinesis() {
        PublicadorDeEventos publicador = new PublicadorDeEventos(sns, kinesis);
        EventoPedido e = evento();

        publicador.publicar(e);

        verify(sns).publicar(e);
        verify(kinesis).publicar(e);
    }

    @Test
    void siSnsFallaKinesisIgualRecibeElEvento() {
        PublicadorDeEventos publicador = new PublicadorDeEventos(sns, kinesis);
        EventoPedido e = evento();
        doThrow(new RuntimeException("SNS caído")).when(sns).publicar(e);

        assertDoesNotThrow(() -> publicador.publicar(e));

        verify(kinesis).publicar(e);
    }

    @Test
    void siKinesisFallaSnsYaRecibioElEvento() {
        PublicadorDeEventos publicador = new PublicadorDeEventos(sns, kinesis);
        EventoPedido e = evento();
        doThrow(new RuntimeException("Kinesis caído")).when(kinesis).publicar(e);

        assertDoesNotThrow(() -> publicador.publicar(e));

        verify(sns).publicar(e);
    }
}