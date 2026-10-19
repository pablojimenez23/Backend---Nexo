package com.nexo.msqueues.config;

import com.nexo.msqueues.dto.QueueCreateRequest;
import com.nexo.msqueues.exception.QueueAlreadyExistsException;
import com.nexo.msqueues.service.SnsAdminService;
import com.nexo.msqueues.service.SqsAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, deja en AWS lo que declara application.yml: primero las colas y sus DLQ,
 * luego los topics y las suscripciones con su filtro por evento. Es idempotente, así que
 * se puede ejecutar en cada réplica y en cada despliegue sin duplicar nada.
 */
@Component
public class QueueBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QueueBootstrap.class);

    private final SqsAdminService sqsAdmin;
    private final SnsAdminService snsAdmin;
    private final SqsProperties sqsProps;
    private final SnsProperties snsProps;

    public QueueBootstrap(SqsAdminService sqsAdmin, SnsAdminService snsAdmin,
                          SqsProperties sqsProps, SnsProperties snsProps) {
        this.sqsAdmin = sqsAdmin;
        this.snsAdmin = snsAdmin;
        this.sqsProps = sqsProps;
        this.snsProps = snsProps;
    }

    @Override
    public void run(ApplicationArguments args) {
        prepararColas();
        prepararTopics();
    }

    private void prepararColas() {
        for (SqsProperties.QueueDef def : sqsProps.queues()) {
            try {
                sqsAdmin.crear(new QueueCreateRequest(def.name(), def.dlqName(), null, null, null));
                log.info("Cola '{}' creada con DLQ '{}'", def.name(), def.dlqName());
            } catch (QueueAlreadyExistsException e) {
                log.info("Cola '{}' ya existía, se mantiene", def.name());
            } catch (Exception e) {
                log.error("No se pudo preparar la cola '{}': {}", def.name(), e.getMessage());
            }
        }
    }

    private void prepararTopics() {
        for (SnsProperties.TopicDef topic : snsProps.topics()) {
            try {
                snsAdmin.asegurarTopic(topic.name());
                log.info("Topic '{}' listo", topic.name());
            } catch (Exception e) {
                log.error("No se pudo preparar el topic '{}': {}", topic.name(), e.getMessage());
                continue;
            }
            for (SnsProperties.SubscriptionDef sub : topic.subscriptions()) {
                try {
                    snsAdmin.asegurarSuscripcion(topic.name(), sub.queue(), sub.eventos());
                } catch (Exception e) {
                    log.error("No se pudo suscribir '{}' al topic '{}': {}", sub.queue(), topic.name(), e.getMessage());
                }
            }
        }
    }
}