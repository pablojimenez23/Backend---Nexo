package com.nexo.msqueues.service;

import com.nexo.msqueues.config.SqsProperties;
import com.nexo.msqueues.dto.MensajeView;
import com.nexo.msqueues.dto.QueueCreateRequest;
import com.nexo.msqueues.dto.QueueResponse;
import com.nexo.msqueues.dto.QueueUpdateRequest;
import com.nexo.msqueues.exception.QueueAlreadyExistsException;
import com.nexo.msqueues.exception.QueueNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.StartMessageMoveTaskRequest;

/**
 * Único punto de acceso a SQS: todas las operaciones de creación, modificación,
 * consulta y eliminación de colas pasan por esta clase.
 */
@Service
public class SqsAdminService {

    private static final Logger log = LoggerFactory.getLogger(SqsAdminService.class);
    private static final String PREFIJO = "nexo-";
    private static final String SUFIJO_DLQ = "-dlq";
    private static final Pattern DLQ_ARN = Pattern.compile("\"deadLetterTargetArn\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MAX_RECEIVE = Pattern.compile("\"maxReceiveCount\"\\s*:\\s*\"?(\\d+)\"?");

    private final SqsClient sqs;
    private final SqsProperties props;

    public SqsAdminService(SqsClient sqs, SqsProperties props) {
        this.sqs = sqs;
        this.props = props;
    }

    // ------------------------------------------------------------------ crear

    public QueueResponse crear(QueueCreateRequest req) {
        if (existe(req.name())) {
            throw new QueueAlreadyExistsException(req.name());
        }
        String dlqName = (req.dlqName() == null || req.dlqName().isBlank()) ? req.name() + SUFIJO_DLQ : req.dlqName();
        if (dlqName.equals(req.name())) {
            throw new IllegalArgumentException("La DLQ no puede llamarse igual que la cola principal");
        }
        int maxReceive = req.maxReceiveCount() != null ? req.maxReceiveCount() : props.defaults().maxReceiveCount();
        int visibility = req.visibilityTimeoutSeconds() != null ? req.visibilityTimeoutSeconds() : props.defaults().visibilityTimeoutSeconds();
        int retention = req.retentionSeconds() != null ? req.retentionSeconds() : props.defaults().retentionSeconds();

        String dlqUrl = existe(dlqName) ? urlDe(dlqName) : sqs.createQueue(
                software.amazon.awssdk.services.sqs.model.CreateQueueRequest.builder()
                        .queueName(dlqName)
                        .attributes(Map.of(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600"))
                        .build()).queueUrl();
        String dlqArn = atributo(dlqUrl, QueueAttributeName.QUEUE_ARN);

        String url = sqs.createQueue(software.amazon.awssdk.services.sqs.model.CreateQueueRequest.builder()
                .queueName(req.name())
                .attributes(Map.of(
                        QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(visibility),
                        QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(retention),
                        QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "10",
                        QueueAttributeName.REDRIVE_POLICY, politicaRedrive(dlqArn, maxReceive)))
                .build()).queueUrl();

        log.info("Cola '{}' creada (DLQ '{}', maxReceiveCount={})", req.name(), dlqName, maxReceive);
        return describir(req.name(), url);
    }

    // -------------------------------------------------------------- modificar

    public QueueResponse actualizar(String name, QueueUpdateRequest req) {
        if (req.visibilityTimeoutSeconds() == null && req.retentionSeconds() == null && req.maxReceiveCount() == null) {
            throw new IllegalArgumentException("Debes indicar al menos un valor para modificar");
        }
        String url = urlDe(name);
        Map<QueueAttributeName, String> cambios = new HashMap<>();
        if (req.visibilityTimeoutSeconds() != null) {
            cambios.put(QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(req.visibilityTimeoutSeconds()));
        }
        if (req.retentionSeconds() != null) {
            cambios.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(req.retentionSeconds()));
        }
        if (req.maxReceiveCount() != null) {
            String actual = atributo(url, QueueAttributeName.REDRIVE_POLICY);
            String dlqArn = actual == null ? null : primero(DLQ_ARN, actual);
            if (dlqArn == null) {
                throw new IllegalArgumentException(
                        "La cola '" + name + "' no tiene DLQ asociada, no se puede cambiar maxReceiveCount");
            }
            cambios.put(QueueAttributeName.REDRIVE_POLICY, politicaRedrive(dlqArn, req.maxReceiveCount()));
        }
        sqs.setQueueAttributes(SetQueueAttributesRequest.builder().queueUrl(url).attributes(cambios).build());
        log.info("Cola '{}' modificada: {}", name, cambios.keySet());
        return describir(name, url);
    }

    // --------------------------------------------------------------- consultar

    public List<QueueResponse> listar() {
        return sqs.listQueues(ListQueuesRequest.builder().queueNamePrefix(PREFIJO).build())
                .queueUrls().stream()
                .map(url -> describir(nombreDesdeUrl(url), url))
                .toList();
    }

    public QueueResponse obtener(String name) {
        return describir(name, urlDe(name));
    }

    public String arnDe(String name) {
        return atributo(urlDe(name), QueueAttributeName.QUEUE_ARN);
    }

    // --------------------------------------------------------------- eliminar

    public void eliminar(String name, boolean conDlq) {
        String url = urlDe(name);
        String redrive = conDlq ? atributo(url, QueueAttributeName.REDRIVE_POLICY) : null;
        sqs.deleteQueue(DeleteQueueRequest.builder().queueUrl(url).build());
        log.info("Cola '{}' eliminada", name);
        String arn = redrive == null ? null : primero(DLQ_ARN, redrive);
        if (arn != null) {
            String dlqName = arn.substring(arn.lastIndexOf(':') + 1);
            try {
                sqs.deleteQueue(DeleteQueueRequest.builder().queueUrl(urlDe(dlqName)).build());
                log.info("DLQ '{}' eliminada", dlqName);
            } catch (QueueNotFoundException ignorado) {
                log.warn("La DLQ '{}' ya no existía", dlqName);
            }
        }
    }

    public void vaciar(String name) {
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(urlDe(name)).build());
        log.info("Cola '{}' vaciada", name);
    }

    // ------------------------------------------------------ mensajes de prueba

    /** Envía un mensaje directo a una cola. Devuelve el id del mensaje. */
    public String enviarMensaje(String name, String body) {
        String id = sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(urlDe(name)).messageBody(body).build()).messageId();
        log.info("Mensaje de prueba {} enviado a '{}'", id, name);
        return id;
    }

    /**
     * Muestra mensajes sin consumirlos (visibilidad 0, así otros consumidores siguen pudiendo
     * recibirlos). Cada lectura suma una recepción al mensaje, por eso se limita a 10.
     */
    public List<MensajeView> verMensajes(String name, int max) {
        var respuesta = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(urlDe(name))
                .maxNumberOfMessages(max)
                .visibilityTimeout(0)
                .waitTimeSeconds(0)
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                .messageAttributeNames("All")
                .build());
        return respuesta.messages().stream()
                .map(m -> new MensajeView(
                        m.messageId(),
                        m.body(),
                        Integer.parseInt(m.attributes().getOrDefault(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "0")),
                        m.messageAttributes().entrySet().stream().collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().stringValue() == null ? "" : e.getValue().stringValue()))))
                .toList();
    }

    /** Devuelve los mensajes de una DLQ a su cola de origen para que se reprocesen. */
    public String reenviarDesdeDlq(String name) {
        if (!name.endsWith(SUFIJO_DLQ)) {
            throw new IllegalArgumentException("Solo se pueden reenviar mensajes desde una DLQ (nombre terminado en -dlq)");
        }
        String arn = arnDe(name);
        String tarea = sqs.startMessageMoveTask(StartMessageMoveTaskRequest.builder().sourceArn(arn).build()).taskHandle();
        log.info("Reenvío de mensajes desde la DLQ '{}' iniciado", name);
        return tarea;
    }

    // ------------------------------------------------- uso por SnsAdminService

    /** Fija la política de acceso de la cola (permite que SNS le entregue mensajes). */
    public void fijarPolitica(String name, String politica) {
        sqs.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(urlDe(name))
                .attributes(Map.of(QueueAttributeName.POLICY, politica))
                .build());
    }

    // ------------------------------------------------------------- utilidades

    private boolean existe(String name) {
        try {
            sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build());
            return true;
        } catch (software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException e) {
            return false;
        }
    }

    private String urlDe(String name) {
        try {
            return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
        } catch (software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException e) {
            throw new QueueNotFoundException(name);
        }
    }

    private String atributo(String url, QueueAttributeName attr) {
        return sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(url).attributeNames(attr).build()).attributes().get(attr);
    }

    private String nombreDesdeUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private String politicaRedrive(String dlqArn, int maxReceive) {
        return "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"" + maxReceive + "\"}";
    }

    private String primero(Pattern patron, String texto) {
        Matcher m = patron.matcher(texto);
        return m.find() ? m.group(1) : null;
    }

    private QueueResponse describir(String name, String url) {
        Map<QueueAttributeName, String> a = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(url).attributeNames(QueueAttributeName.ALL).build()).attributes();
        String redrive = a.get(QueueAttributeName.REDRIVE_POLICY);
        String dlqArn = redrive == null ? null : primero(DLQ_ARN, redrive);
        String max = redrive == null ? null : primero(MAX_RECEIVE, redrive);
        return new QueueResponse(name, url, a.get(QueueAttributeName.QUEUE_ARN),
                Long.parseLong(a.getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0")),
                Long.parseLong(a.getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0")),
                Integer.parseInt(a.getOrDefault(QueueAttributeName.VISIBILITY_TIMEOUT, "30")),
                Integer.parseInt(a.getOrDefault(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "345600")),
                dlqArn, max == null ? null : Integer.valueOf(max));
    }
}