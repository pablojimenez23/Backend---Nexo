package com.nexo.msqueues.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexo.msqueues.config.SqsProperties;
import com.nexo.msqueues.dto.QueueCreateRequest;
import com.nexo.msqueues.dto.QueueResponse;
import com.nexo.msqueues.dto.QueueUpdateRequest;
import com.nexo.msqueues.exception.QueueAlreadyExistsException;
import com.nexo.msqueues.exception.QueueNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.StartMessageMoveTaskRequest;
import software.amazon.awssdk.services.sqs.model.StartMessageMoveTaskResponse;

/** Lógica de administración de colas con SQS simulado (no usa AWS). */
class SqsAdminServiceTest {

    private static final String BASE = "https://sqs.us-east-1.amazonaws.com/123456789012/";
    private static final String ARN = "arn:aws:sqs:us-east-1:123456789012:";

    private SqsClient sqs;
    private SqsAdminService service;

    @BeforeEach
    void preparar() {
        sqs = mock(SqsClient.class);
        service = new SqsAdminService(sqs,
                new SqsProperties("us-east-1", new SqsProperties.Defaults(3, 30, 345600), List.of()));
    }

    /** Hace que la cola exista, con la política de redrive indicada (o ninguna). */
    private void colaExiste(String name, String redrive) {
        when(sqs.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenAnswer(inv -> {
                    GetQueueUrlRequest r = inv.getArgument(0);
                    if (r.queueName().equals(name)) {
                        return GetQueueUrlResponse.builder().queueUrl(BASE + name).build();
                    }
                    throw QueueDoesNotExistException.builder().message("no existe").build();
                });
        Map<QueueAttributeName, String> attrs = new java.util.HashMap<>();
        attrs.put(QueueAttributeName.QUEUE_ARN, ARN + name);
        attrs.put(QueueAttributeName.VISIBILITY_TIMEOUT, "30");
        if (redrive != null) {
            attrs.put(QueueAttributeName.REDRIVE_POLICY, redrive);
        }
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(GetQueueAttributesResponse.builder().attributes(attrs).build());
    }

    private void colaNoExiste() {
        when(sqs.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenThrow(QueueDoesNotExistException.builder().message("no existe").build());
    }

    @Test
    void crearConfiguraDlqYPoliticaDeReintentos() {
        colaNoExiste();
        when(sqs.createQueue(any(CreateQueueRequest.class))).thenAnswer(inv -> {
            CreateQueueRequest r = inv.getArgument(0);
            return CreateQueueResponse.builder().queueUrl(BASE + r.queueName()).build();
        });
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(
                GetQueueAttributesResponse.builder().attributes(Map.of(
                        QueueAttributeName.QUEUE_ARN, ARN + "nexo-prueba")).build());

        service.crear(new QueueCreateRequest("nexo-prueba", null, 5, 45, 7200));

        ArgumentCaptor<CreateQueueRequest> cap = ArgumentCaptor.forClass(CreateQueueRequest.class);
        verify(sqs, org.mockito.Mockito.times(2)).createQueue(cap.capture());
        assertThat(cap.getAllValues().get(0).queueName()).isEqualTo("nexo-prueba-dlq");
        CreateQueueRequest principal = cap.getAllValues().get(1);
        assertThat(principal.queueName()).isEqualTo("nexo-prueba");
        assertThat(principal.attributes().get(QueueAttributeName.VISIBILITY_TIMEOUT)).isEqualTo("45");
        assertThat(principal.attributes().get(QueueAttributeName.MESSAGE_RETENTION_PERIOD)).isEqualTo("7200");
        assertThat(principal.attributes().get(QueueAttributeName.REDRIVE_POLICY))
                .contains("\"maxReceiveCount\":\"5\"")
                .contains("deadLetterTargetArn");
    }

    @Test
    void crearSinValoresUsaLosPorDefecto() {
        colaNoExiste();
        when(sqs.createQueue(any(CreateQueueRequest.class))).thenAnswer(inv -> {
            CreateQueueRequest r = inv.getArgument(0);
            return CreateQueueResponse.builder().queueUrl(BASE + r.queueName()).build();
        });
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(
                GetQueueAttributesResponse.builder().attributes(Map.of(
                        QueueAttributeName.QUEUE_ARN, ARN + "x")).build());

        service.crear(new QueueCreateRequest("nexo-prueba", null, null, null, null));

        ArgumentCaptor<CreateQueueRequest> cap = ArgumentCaptor.forClass(CreateQueueRequest.class);
        verify(sqs, org.mockito.Mockito.times(2)).createQueue(cap.capture());
        CreateQueueRequest principal = cap.getAllValues().get(1);
        assertThat(principal.attributes().get(QueueAttributeName.VISIBILITY_TIMEOUT)).isEqualTo("30");
        assertThat(principal.attributes().get(QueueAttributeName.REDRIVE_POLICY)).contains("\"maxReceiveCount\":\"3\"");
    }

    @Test
    void crearColaExistenteLanzaConflicto() {
        colaExiste("nexo-prueba", null);
        assertThatThrownBy(() -> service.crear(new QueueCreateRequest("nexo-prueba", null, null, null, null)))
                .isInstanceOf(QueueAlreadyExistsException.class);
        verify(sqs, never()).createQueue(any(CreateQueueRequest.class));
    }

    @Test
    void crearConDlqIgualAlNombreSeRechaza() {
        colaNoExiste();
        assertThatThrownBy(() -> service.crear(new QueueCreateRequest("nexo-a-dlq", "nexo-a-dlq", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sqs, never()).createQueue(any(CreateQueueRequest.class));
    }

    @Test
    void obtenerColaInexistenteLanzaNoEncontrada() {
        colaNoExiste();
        assertThatThrownBy(() -> service.obtener("nexo-fantasma")).isInstanceOf(QueueNotFoundException.class);
    }

    @Test
    void obtenerLeeDlqYMaxReceiveCountDeLaPolitica() {
        colaExiste("nexo-stock",
                "{\"deadLetterTargetArn\":\"" + ARN + "nexo-stock-dlq\",\"maxReceiveCount\":\"4\"}");
        QueueResponse r = service.obtener("nexo-stock");
        assertThat(r.name()).isEqualTo("nexo-stock");
        assertThat(r.dlqArn()).isEqualTo(ARN + "nexo-stock-dlq");
        assertThat(r.maxReceiveCount()).isEqualTo(4);
    }

    @Test
    void actualizarSinCambiosSeRechaza() {
        assertThatThrownBy(() -> service.actualizar("nexo-stock", new QueueUpdateRequest(null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sqs, never()).setQueueAttributes(any(SetQueueAttributesRequest.class));
    }

    @Test
    void actualizarCambiaSoloLoInformado() {
        colaExiste("nexo-stock", null);
        service.actualizar("nexo-stock", new QueueUpdateRequest(120, null, null));

        ArgumentCaptor<SetQueueAttributesRequest> cap = ArgumentCaptor.forClass(SetQueueAttributesRequest.class);
        verify(sqs).setQueueAttributes(cap.capture());
        assertThat(cap.getValue().attributes()).containsOnlyKeys(QueueAttributeName.VISIBILITY_TIMEOUT);
        assertThat(cap.getValue().attributes().get(QueueAttributeName.VISIBILITY_TIMEOUT)).isEqualTo("120");
    }

    @Test
    void cambiarMaxReceiveCountSinDlqSeRechaza() {
        colaExiste("nexo-stock", null);
        assertThatThrownBy(() -> service.actualizar("nexo-stock", new QueueUpdateRequest(null, null, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DLQ");
    }

    @Test
    void cambiarMaxReceiveCountConservaLaDlq() {
        colaExiste("nexo-stock",
                "{\"deadLetterTargetArn\":\"" + ARN + "nexo-stock-dlq\",\"maxReceiveCount\":\"3\"}");
        service.actualizar("nexo-stock", new QueueUpdateRequest(null, null, 7));

        ArgumentCaptor<SetQueueAttributesRequest> cap = ArgumentCaptor.forClass(SetQueueAttributesRequest.class);
        verify(sqs).setQueueAttributes(cap.capture());
        assertThat(cap.getValue().attributes().get(QueueAttributeName.REDRIVE_POLICY))
                .contains(ARN + "nexo-stock-dlq").contains("\"maxReceiveCount\":\"7\"");
    }

    @Test
    void vaciarLlamaAPurge() {
        colaExiste("nexo-stock", null);
        service.vaciar("nexo-stock");
        ArgumentCaptor<PurgeQueueRequest> cap = ArgumentCaptor.forClass(PurgeQueueRequest.class);
        verify(sqs).purgeQueue(cap.capture());
        assertThat(cap.getValue().queueUrl()).isEqualTo(BASE + "nexo-stock");
    }

    @Test
    void enviarMensajeDevuelveElId() {
        colaExiste("nexo-stock", null);
        when(sqs.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("m-1").build());
        assertThat(service.enviarMensaje("nexo-stock", "{\"a\":1}")).isEqualTo("m-1");
    }

    @Test
    void verMensajesNoLosConsume() {
        colaExiste("nexo-stock", null);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().messageId("m-1").body("hola")
                        .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "2")).build())
                .build());

        var vistos = service.verMensajes("nexo-stock", 5);

        assertThat(vistos).hasSize(1);
        assertThat(vistos.get(0).receiveCount()).isEqualTo(2);
        ArgumentCaptor<ReceiveMessageRequest> cap = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqs).receiveMessage(cap.capture());
        assertThat(cap.getValue().visibilityTimeout()).isZero();   // se ve pero no se bloquea
        assertThat(cap.getValue().maxNumberOfMessages()).isEqualTo(5);
    }

    @Test
    void reenviarSoloDesdeUnaDlq() {
        assertThatThrownBy(() -> service.reenviarDesdeDlq("nexo-stock"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sqs, never()).startMessageMoveTask(any(StartMessageMoveTaskRequest.class));
    }

    @Test
    void reenviarDesdeDlqIniciaLaTarea() {
        colaExiste("nexo-stock-dlq", null);
        when(sqs.startMessageMoveTask(any(StartMessageMoveTaskRequest.class)))
                .thenReturn(StartMessageMoveTaskResponse.builder().taskHandle("tarea-1").build());
        assertThat(service.reenviarDesdeDlq("nexo-stock-dlq")).isEqualTo("tarea-1");
    }
}