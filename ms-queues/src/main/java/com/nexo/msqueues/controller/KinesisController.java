package com.nexo.msqueues.controller;

import com.nexo.msqueues.dto.RegistroKinesis;
import com.nexo.msqueues.service.KinesisAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

/** Consulta de los streams de Kinesis para el panel de administración. Solo lectura y solo ADMIN. */
@RestController
@RequestMapping("/streams")
public class KinesisController {

    private static final Pattern NOMBRE = Pattern.compile("^nexo-[a-z0-9-]{1,60}$");

    private final KinesisAdminService servicio;

    public KinesisController(KinesisAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<String> listar(JwtAuthenticationToken auth) {
        exigirAdmin((Jwt) auth.getPrincipal());
        return servicio.listar();
    }

    @GetMapping("/{nombre}/registros")
    public List<RegistroKinesis> registros(@PathVariable String nombre,
                                           @RequestParam(defaultValue = "10") int limite,
                                           JwtAuthenticationToken auth) {
        exigirAdmin((Jwt) auth.getPrincipal());
        if (!NOMBRE.matcher(nombre).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre debe empezar con nexo- y usar minúsculas, números y guiones");
        }
        if (limite < 1 || limite > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El límite debe estar entre 1 y 100");
        }
        return servicio.leerRegistros(nombre, limite);
    }

    private static void exigirAdmin(Jwt jwt) {
        List<String> grupos = jwt.getClaimAsStringList("cognito:groups");
        boolean esAdmin = grupos != null && grupos.stream().anyMatch(g -> g.equalsIgnoreCase("admin"));
        if (!esAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administradores");
        }
    }
}