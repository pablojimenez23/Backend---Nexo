package com.nexo.msqueues.service;

import com.nexo.msqueues.dto.RegistroKinesis;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamSummaryRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ResourceInUseException;
import software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Único acceso a Kinesis de ms-queues: crea los streams declarados, los lista y lee sus registros.
 * Los controladores solo validan y delegan.
 */
@Service
public class KinesisAdminService {

    private final KinesisClient kinesis;

    public KinesisAdminService(KinesisClient kinesis) {
        this.kinesis = kinesis;
    }

    /** Crea el stream si no existe. Devuelve true si lo creó y false si ya estaba. Es idempotente. */
    public boolean asegurarStream(String nombre, int shards) {
        try {
            kinesis.describeStreamSummary(DescribeStreamSummaryRequest.builder().streamName(nombre).build());
            return false;
        } catch (ResourceNotFoundException e) {
            try {
                kinesis.createStream(CreateStreamRequest.builder().streamName(nombre).shardCount(shards).build());
                return true;
            } catch (ResourceInUseException enUso) {
                return false; // otra réplica lo creó al mismo tiempo
            }
        }
    }

    public List<String> listar() {
        return kinesis.listStreams().streamNames();
    }

    /** Lee los últimos 'limite' registros que conserva el stream (los más recientes primero), sin consumirlos. */
    public List<RegistroKinesis> leerRegistros(String nombre, int limite) {
        List<Shard> shards;
        try {
            shards = kinesis.listShards(ListShardsRequest.builder().streamName(nombre).build()).shards();
        } catch (ResourceNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El stream no existe");
        }

        Deque<RegistroKinesis> recientes = new ArrayDeque<>();
        for (Shard shard : shards) {
            String iterador = kinesis.getShardIterator(GetShardIteratorRequest.builder()
                    .streamName(nombre)
                    .shardId(shard.shardId())
                    .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
                    .build()).shardIterator();

            // Kinesis entrega los registros por lotes: se recorre hasta llegar al final del shard.
            for (int vuelta = 0; vuelta < 200 && iterador != null; vuelta++) {
                var respuesta = kinesis.getRecords(GetRecordsRequest.builder()
                        .shardIterator(iterador)
                        .limit(1000)
                        .build());

                for (software.amazon.awssdk.services.kinesis.model.Record r : respuesta.records()) {
                    recientes.addLast(new RegistroKinesis(shard.shardId(), r.sequenceNumber(),
                            r.approximateArrivalTimestamp(), r.partitionKey(), r.data().asUtf8String()));
                    if (recientes.size() > limite) {
                        recientes.removeFirst();
                    }
                }

                iterador = respuesta.nextShardIterator();
                Long atraso = respuesta.millisBehindLatest();
                if (atraso == null || atraso == 0) {
                    break;
                }
            }
        }

        List<RegistroKinesis> resultado = new ArrayList<>();
        recientes.descendingIterator().forEachRemaining(resultado::add);
        return resultado;
    }
}