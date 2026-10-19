package com.nexo.msqueues.controller;

import com.nexo.msqueues.dto.MensajeRequest;
import com.nexo.msqueues.dto.MensajeView;
import com.nexo.msqueues.dto.Patrones;
import com.nexo.msqueues.dto.QueueCreateRequest;
import com.nexo.msqueues.dto.QueueResponse;
import com.nexo.msqueues.dto.QueueUpdateRequest;
import com.nexo.msqueues.service.SqsAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de administración de colas SQS. Solo para el grupo ADMIN de Cognito.
 *
 * POST   /queues                  crear cola (con su DLQ)
 * GET    /queues                  listar colas
 * GET    /queues/{name}           consultar una cola
 * PUT    /queues/{name}           modificar visibilidad, retención o reintentos
 * DELETE /queues/{name}           eliminar (?withDlq=true elimina también la DLQ)
 * POST   /queues/{name}/purge     vaciar mensajes
 * POST   /queues/{name}/messages  enviar mensaje de prueba
 * GET    /queues/{name}/messages  ver mensajes sin consumirlos
 * POST   /queues/{name}/redrive   reenviar mensajes de una DLQ a su cola de origen
 */
@RestController
@RequestMapping("/queues")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class QueueController {

    private final SqsAdminService admin;

    public QueueController(SqsAdminService admin) {
        this.admin = admin;
    }

    @PostMapping
    public ResponseEntity<QueueResponse> crear(@Valid @RequestBody QueueCreateRequest request) {
        QueueResponse creada = admin.crear(request);
        return ResponseEntity.created(URI.create("/messaging/queues/" + creada.name())).body(creada);
    }

    @GetMapping
    public List<QueueResponse> listar() {
        return admin.listar();
    }

    @GetMapping("/{name}")
    public QueueResponse obtener(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name) {
        return admin.obtener(name);
    }

    @PutMapping("/{name}")
    public QueueResponse actualizar(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name,
            @Valid @RequestBody QueueUpdateRequest request) {
        return admin.actualizar(name, request);
    }

    @PostMapping("/{name}/purge")
    public ResponseEntity<Void> vaciar(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name) {
        admin.vaciar(name);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> eliminar(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name,
            @RequestParam(defaultValue = "false") boolean withDlq) {
        admin.eliminar(name, withDlq);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{name}/messages")
    public ResponseEntity<Map<String, String>> enviarMensaje(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name,
            @Valid @RequestBody MensajeRequest request) {
        String id = admin.enviarMensaje(name, request.body());
        return ResponseEntity.status(201).body(Map.of("messageId", id, "queue", name));
    }

    @GetMapping("/{name}/messages")
    public List<MensajeView> verMensajes(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name,
            @RequestParam(defaultValue = "5")
            @Min(value = 1, message = "max mínimo 1") @Max(value = 10, message = "max máximo 10") int max) {
        return admin.verMensajes(name, max);
    }

    @PostMapping("/{name}/redrive")
    public ResponseEntity<Map<String, String>> reenviar(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name) {
        String tarea = admin.reenviarDesdeDlq(name);
        return ResponseEntity.accepted().body(Map.of("taskHandle", tarea, "dlq", name));
    }
}