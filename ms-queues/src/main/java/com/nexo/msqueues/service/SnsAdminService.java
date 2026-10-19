package com.nexo.msqueues.service;

import com.nexo.msqueues.dto.PublicarRequest;
import com.nexo.msqueues.dto.PublicarResponse;
import com.nexo.msqueues.dto.SuscripcionRequest;
import com.nexo.msqueues.dto.SuscripcionView;
import com.nexo.msqueues.dto.TopicResponse;
import com.nexo.msqueues.exception.SuscripcionAlreadyExistsException;
import com.nexo.msqueues.exception.SuscripcionNotFoundException;
import com.nexo.msqueues.exception.TopicAlreadyExistsException;
import com.nexo.msqueues.exception.TopicNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.DeleteTopicRequest;
import software.amazon.awssdk.services.sns.model.GetSubscriptionAttributesRequest;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicRequest;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.SetSubscriptionAttributesRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.Subscription;
import software.amazon.awssdk.services.sns.model.Topic;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;

/**
 * Único punto de acceso a SNS: topics (destinos de publicación) y suscripciones de colas
 * con su filtro por evento (reglas de distribución).
 *
 * Los productores publican con el atributo de mensaje "evento"; cada suscripción solo recibe
 * los eventos de su política de filtro.
 */
@Service
public class SnsAdminService {

    private static final Logger log = LoggerFactory.getLogger(SnsAdminService.class);
    private static final String PREFIJO = "nexo-";
    private static final String ATRIBUTO_EVENTO = "evento";
    private static final Pattern TEXTO_ENTRE_COMILLAS = Pattern.compile("\"([^\"]+)\"");

    private final SnsClient sns;
    private final SqsAdminService sqsAdmin;

    public SnsAdminService(SnsClient sns, SqsAdminService sqsAdmin) {
        this.sns = sns;
        this.sqsAdmin = sqsAdmin;
    }

    // ----------------------------------------------------------------- topics

    public TopicResponse crear(String name) {
        if (buscarArn(name).isPresent()) {
            throw new TopicAlreadyExistsException(name);
        }
        String arn = sns.createTopic(CreateTopicRequest.builder().name(name).build()).topicArn();
        log.info("Topic '{}' creado", name);
        return new TopicResponse(name, arn, List.of());
    }

    /** Crea el topic si no existe (SNS es idempotente). Lo usa el arranque. */
    public String asegurarTopic(String name) {
        return sns.createTopic(CreateTopicRequest.builder().name(name).build()).topicArn();
    }

    public List<TopicResponse> listar() {
        return sns.listTopicsPaginator().topics().stream()
                .map(Topic::topicArn)
                .filter(arn -> nombreDe(arn).startsWith(PREFIJO))
                .map(this::describir)
                .toList();
    }

    public TopicResponse obtener(String name) {
        return describir(arnDe(name));
    }

    public void eliminar(String name) {
        sns.deleteTopic(DeleteTopicRequest.builder().topicArn(arnDe(name)).build());
        log.info("Topic '{}' eliminado", name);
    }

    // ---------------------------------------------------------- suscripciones

    public SuscripcionView crearSuscripcion(String topic, SuscripcionRequest req) {
        String topicArn = arnDe(topic);
        String queueArn = sqsAdmin.arnDe(req.queue());
        if (buscarSuscripcion(topicArn, queueArn).isPresent()) {
            throw new SuscripcionAlreadyExistsException(topic, req.queue());
        }
        return aplicar(topicArn, req.queue(), queueArn, req.eventos());
    }

    public SuscripcionView modificarSuscripcion(String topic, String queue, List<String> eventos) {
        String topicArn = arnDe(topic);
        String queueArn = sqsAdmin.arnDe(queue);
        if (buscarSuscripcion(topicArn, queueArn).isEmpty()) {
            throw new SuscripcionNotFoundException(topic, queue);
        }
        return aplicar(topicArn, queue, queueArn, eventos);
    }

    /** Crea o actualiza la suscripción. Lo usa el arranque para aplicar application.yml. */
    public SuscripcionView asegurarSuscripcion(String topic, String queue, List<String> eventos) {
        return aplicar(arnDe(topic), queue, sqsAdmin.arnDe(queue), eventos);
    }

    public void eliminarSuscripcion(String topic, String queue) {
        String topicArn = arnDe(topic);
        String queueArn = sqsAdmin.arnDe(queue);
        Subscription s = buscarSuscripcion(topicArn, queueArn)
                .orElseThrow(() -> new SuscripcionNotFoundException(topic, queue));
        sns.unsubscribe(UnsubscribeRequest.builder().subscriptionArn(s.subscriptionArn()).build());
        log.info("Cola '{}' desuscrita del topic '{}'", queue, topic);
    }

    // -------------------------------------------------------------- publicar

    /** Publica un evento de prueba y devuelve a qué colas llegará según los filtros actuales. */
    public PublicarResponse publicar(String topic, PublicarRequest req) {
        String topicArn = arnDe(topic);
        String id = sns.publish(software.amazon.awssdk.services.sns.model.PublishRequest.builder()
                .topicArn(topicArn)
                .message(req.mensaje())
                .messageAttributes(Map.of(ATRIBUTO_EVENTO, MessageAttributeValue.builder()
                        .dataType("String").stringValue(req.evento()).build()))
                .build()).messageId();

        List<String> destino = describir(topicArn).suscripciones().stream()
                .filter(s -> s.eventos().isEmpty() || s.eventos().contains(req.evento()))
                .map(SuscripcionView::queue)
                .toList();
        log.info("Evento '{}' publicado en '{}' (id {}), destino: {}", req.evento(), topic, id, destino);
        return new PublicarResponse(id, topic, req.evento(), destino);
    }

    // ------------------------------------------------------------- utilidades

    private SuscripcionView aplicar(String topicArn, String queue, String queueArn, List<String> eventos) {
        List<String> unicos = eventos.stream().distinct().toList();
        permitirPublicacion(queue, queueArn);
        String filtro = filtro(unicos);

        Optional<Subscription> existente = buscarSuscripcion(topicArn, queueArn);
        String suscripcionArn;
        if (existente.isPresent()) {
            suscripcionArn = existente.get().subscriptionArn();
            sns.setSubscriptionAttributes(SetSubscriptionAttributesRequest.builder()
                    .subscriptionArn(suscripcionArn)
                    .attributeName("FilterPolicy")
                    .attributeValue(filtro)
                    .build());
        } else {
            suscripcionArn = sns.subscribe(SubscribeRequest.builder()
                    .topicArn(topicArn)
                    .protocol("sqs")
                    .endpoint(queueArn)
                    .attributes(Map.of("RawMessageDelivery", "true", "FilterPolicy", filtro))
                    .returnSubscriptionArn(true)
                    .build()).subscriptionArn();
        }
        log.info("Cola '{}' recibe de '{}' los eventos {}", queue, nombreDe(topicArn), unicos);
        return new SuscripcionView(queue, queueArn, unicos, suscripcionArn);
    }

    /**
     * Política de la cola: permite que cualquier topic "nexo-*" de la misma cuenta le entregue
     * mensajes. Es una sola política por cola, así no hay que fusionar políticas al suscribir.
     */
    private void permitirPublicacion(String queue, String queueArn) {
        String[] partes = queueArn.split(":"); // arn:aws:sqs:region:cuenta:nombre
        String politica = "{\"Version\":\"2012-10-17\",\"Statement\":[{"
                + "\"Sid\":\"nexo-sns-entrega\",\"Effect\":\"Allow\","
                + "\"Principal\":{\"Service\":\"sns.amazonaws.com\"},"
                + "\"Action\":\"sqs:SendMessage\",\"Resource\":\"" + queueArn + "\","
                + "\"Condition\":{\"ArnLike\":{\"aws:SourceArn\":\"arn:aws:sns:" + partes[3] + ":" + partes[4] + ":nexo-*\"}}}]}";
        sqsAdmin.fijarPolitica(queue, politica);
    }

    private String filtro(List<String> eventos) {
        return "{\"" + ATRIBUTO_EVENTO + "\":["
                + eventos.stream().map(e -> "\"" + e + "\"").collect(Collectors.joining(","))
                + "]}";
    }

    private List<String> eventosDe(String filtro) {
        if (filtro == null || filtro.isBlank()) {
            return List.of();
        }
        int abre = filtro.indexOf('[');
        int cierra = filtro.indexOf(']');
        if (abre < 0 || cierra < abre) {
            return List.of();
        }
        List<String> eventos = new ArrayList<>();
        Matcher m = TEXTO_ENTRE_COMILLAS.matcher(filtro.substring(abre, cierra));
        while (m.find()) {
            eventos.add(m.group(1));
        }
        return eventos;
    }

    private TopicResponse describir(String topicArn) {
        List<SuscripcionView> suscripciones = suscripcionesDe(topicArn).stream()
                .filter(s -> "sqs".equals(s.protocol()))
                .map(this::vista)
                .toList();
        return new TopicResponse(nombreDe(topicArn), topicArn, suscripciones);
    }

    private SuscripcionView vista(Subscription s) {
        Map<String, String> atributos = sns.getSubscriptionAttributes(GetSubscriptionAttributesRequest.builder()
                .subscriptionArn(s.subscriptionArn()).build()).attributes();
        return new SuscripcionView(nombreDe(s.endpoint()), s.endpoint(),
                eventosDe(atributos.get("FilterPolicy")), s.subscriptionArn());
    }

    private List<Subscription> suscripcionesDe(String topicArn) {
        return sns.listSubscriptionsByTopicPaginator(
                        ListSubscriptionsByTopicRequest.builder().topicArn(topicArn).build())
                .subscriptions().stream().toList();
    }

    private Optional<Subscription> buscarSuscripcion(String topicArn, String queueArn) {
        return suscripcionesDe(topicArn).stream().filter(s -> queueArn.equals(s.endpoint())).findFirst();
    }

    private Optional<String> buscarArn(String name) {
        return sns.listTopicsPaginator().topics().stream()
                .map(Topic::topicArn)
                .filter(arn -> arn.endsWith(":" + name))
                .findFirst();
    }

    private String arnDe(String name) {
        return buscarArn(name).orElseThrow(() -> new TopicNotFoundException(name));
    }

    private String nombreDe(String arn) {
        return arn.substring(arn.lastIndexOf(':') + 1);
    }
}