package com.nexo.msqueues.controller;

import com.nexo.msqueues.dto.Patrones;
import com.nexo.msqueues.dto.PublicarRequest;
import com.nexo.msqueues.dto.PublicarResponse;
import com.nexo.msqueues.dto.SuscripcionRequest;
import com.nexo.msqueues.dto.SuscripcionUpdateRequest;
import com.nexo.msqueues.dto.SuscripcionView;
import com.nexo.msqueues.dto.TopicCreateRequest;
import com.nexo.msqueues.dto.TopicResponse;
import com.nexo.msqueues.service.SnsAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de administración de topics SNS y de las reglas de distribución (qué eventos recibe cada cola).
 * Solo para el grupo ADMIN de Cognito.
 *
 * POST   /topics                              crear topic
 * GET    /topics                              listar topics con sus suscripciones
 * GET    /topics/{name}                       consultar un topic
 * DELETE /topics/{name}                       eliminar topic
 * POST   /topics/{name}/subscriptions         suscribir una cola a ciertos eventos
 * PUT    /topics/{name}/subscriptions/{queue} cambiar los eventos de una suscripción
 * DELETE /topics/{name}/subscriptions/{queue} quitar la suscripción
 * POST   /topics/{name}/publish               publicar un evento de prueba
 */
@RestController
@RequestMapping("/topics")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class TopicController {

    private final SnsAdminService admin;

    public TopicController(SnsAdminService admin) {
        this.admin = admin;
    }

    @PostMapping
    public ResponseEntity<TopicResponse> crear(@Valid @RequestBody TopicCreateRequest request) {
        TopicResponse creado = admin.crear(request.name());
        return ResponseEntity.created(URI.create("/messaging/topics/" + creado.name())).body(creado);
    }

    @GetMapping
    public List<TopicResponse> listar() {
        return admin.listar();
    }

    @GetMapping("/{name}")
    public TopicResponse obtener(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name) {
        return admin.obtener(name);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> eliminar(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name) {
        admin.eliminar(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{name}/subscriptions")
    public ResponseEntity<SuscripcionView> suscribir(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name,
            @Valid @RequestBody SuscripcionRequest request) {
        return ResponseEntity.status(201).body(admin.crearSuscripcion(name, request));
    }

    @PutMapping("/{name}/subscriptions/{queue}")
    public SuscripcionView modificarSuscripcion(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name,
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String queue,
            @Valid @RequestBody SuscripcionUpdateRequest request) {
        return admin.modificarSuscripcion(name, queue, request.eventos());
    }

    @DeleteMapping("/{name}/subscriptions/{queue}")
    public ResponseEntity<Void> desuscribir(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name,
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String queue) {
        admin.eliminarSuscripcion(name, queue);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{name}/publish")
    public PublicarResponse publicar(
            @PathVariable @Pattern(regexp = Patrones.RECURSO, message = Patrones.MENSAJE_RECURSO) String name,
            @Valid @RequestBody PublicarRequest request) {
        return admin.publicar(name, request);
    }
}