package com.nexo.msqueues.service;

import com.nexo.msqueues.dto.RegistroKinesis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KinesisAdminServiceTest {

    @Mock private KinesisClient kinesis;
    private KinesisAdminService servicio;

    @BeforeEach
    void preparar() {
        servicio = new KinesisAdminService(kinesis);
    }

    @Test
    void creaElStreamSiNoExiste() {
        when(kinesis.describeStreamSummary(any(DescribeStreamSummaryRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("no existe").build());

        boolean creado = servicio.asegurarStream("nexo-pedidos-stream", 1);

        assertTrue(creado);
        verify(kinesis).createStream(any(CreateStreamRequest.class));
    }

    @Test
    void noVuelveACrearUnStreamExistente() {
        when(kinesis.describeStreamSummary(any(DescribeStreamSummaryRequest.class)))
                .thenReturn(DescribeStreamSummaryResponse.builder().build());

        boolean creado = servicio.asegurarStream("nexo-pedidos-stream", 1);

        assertFalse(creado);
        verify(kinesis, never()).createStream(any(CreateStreamRequest.class));
    }

    @Test
    void siOtraReplicaLoCreoAlMismoTiempoNoFalla() {
        when(kinesis.describeStreamSummary(any(DescribeStreamSummaryRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("no existe").build());
        when(kinesis.createStream(any(CreateStreamRequest.class)))
                .thenThrow(ResourceInUseException.builder().message("en uso").build());

        assertFalse(servicio.asegurarStream("nexo-pedidos-stream", 1));
    }

    @Test
    void listaLosStreams() {
        when(kinesis.listStreams()).thenReturn(ListStreamsResponse.builder()
                .streamNames("nexo-pedidos-stream").build());

        assertEquals(List.of("nexo-pedidos-stream"), servicio.listar());
    }

    @Test
    void leeLosRegistrosSinConsumirlos() {
        when(kinesis.listShards(any(ListShardsRequest.class))).thenReturn(ListShardsResponse.builder()
                .shards(Shard.builder().shardId("shardId-000000000000").build()).build());
        when(kinesis.getShardIterator(any(GetShardIteratorRequest.class)))
                .thenReturn(GetShardIteratorResponse.builder().shardIterator("it-1").build());
        when(kinesis.getRecords(any(GetRecordsRequest.class))).thenReturn(GetRecordsResponse.builder()
                .records(software.amazon.awssdk.services.kinesis.model.Record.builder()
                        .sequenceNumber("49600")
                        .partitionKey("pedido-1")
                        .approximateArrivalTimestamp(Instant.parse("2026-11-16T12:00:00Z"))
                        .data(SdkBytes.fromUtf8String("{\"evento\":\"pedido.pagado\"}"))
                        .build())
                .build());

        List<RegistroKinesis> registros = servicio.leerRegistros("nexo-pedidos-stream", 5);

        assertEquals(1, registros.size());
        assertEquals("pedido-1", registros.get(0).claveParticion());
        assertEquals("{\"evento\":\"pedido.pagado\"}", registros.get(0).datos());
    }

    @Test
    void unStreamInexistenteResponde404() {
        when(kinesis.listShards(any(ListShardsRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("no existe").build());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.leerRegistros("nexo-otro", 5));
        assertEquals(404, e.getStatusCode().value());
    }
}