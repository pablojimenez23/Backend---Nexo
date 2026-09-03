package com.nexo.msusers.controller;

import com.nexo.msusers.client.CognitoUserInfoClient;
import com.nexo.msusers.dto.ConductorRequestDTO;
import com.nexo.msusers.dto.ConductorResponseDTO;
import com.nexo.msusers.dto.RechazoConductorRequestDTO;
import com.nexo.msusers.entity.Conductor;
import com.nexo.msusers.entity.Notificacion;
import com.nexo.msusers.entity.Usuario;
import com.nexo.msusers.repository.ConductorRepository;
import com.nexo.msusers.repository.NotificacionRepository;
import com.nexo.msusers.repository.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/conductores")
public class ConductorController {

    private final ConductorRepository conductorRepository;
    private final UsuarioRepository usuarioRepository;
    private final CognitoUserInfoClient userInfoClient;
    private final NotificacionRepository notificacionRepository;

    public ConductorController(ConductorRepository conductorRepository,
                                UsuarioRepository usuarioRepository,
                                CognitoUserInfoClient userInfoClient,
                                NotificacionRepository notificacionRepository) {
        this.conductorRepository = conductorRepository;
        this.usuarioRepository = usuarioRepository;
        this.userInfoClient = userInfoClient;
        this.notificacionRepository = notificacionRepository;
    }

    @PostMapping
    public ResponseEntity<ConductorResponseDTO> registrarse(
            @RequestBody ConductorRequestDTO request, JwtAuthenticationToken auth) {

        Usuario usuario = obtenerUsuarioActual(auth);

        if (conductorRepository.findByUsuarioId(usuario.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya tenés un perfil de conductor");
        }

        Conductor conductor = new Conductor();
        conductor.setUsuario(usuario);
        conductor.setNombre(request.nombre());
        conductor.setVehiculo(request.vehiculo());
        conductor.setPatente(request.patente());

        Conductor guardado = conductorRepository.save(conductor);

        notificar(usuario.getCognitoSub(), "Solicitud de conductor enviada",
                "Tu perfil está en revisión. Te avisamos apenas lo aprobemos.");

        return ResponseEntity.status(HttpStatus.CREATED).body(ConductorResponseDTO.desde(guardado));
    }

    @GetMapping("/me")
    public ConductorResponseDTO miPerfil(JwtAuthenticationToken auth) {
        Usuario usuario = obtenerUsuarioActual(auth);
        Conductor conductor = conductorRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tenés perfil de conductor"));
        return ConductorResponseDTO.desde(conductor);
    }

    @PatchMapping("/me/estado")
    public ConductorResponseDTO cambiarEstado(@RequestParam String estado, JwtAuthenticationToken auth) {
        Usuario usuario = obtenerUsuarioActual(auth);
        Conductor conductor = conductorRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tenés perfil de conductor"));

        if (conductor.getEstado() == Conductor.Estado.PENDIENTE_APROBACION) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu perfil todavía no fue aprobado");
        }
        if (conductor.getEstado() == Conductor.Estado.RECHAZADO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu perfil fue rechazado");
        }
        if (conductor.getEstado() == Conductor.Estado.OCUPADO && "DISPONIBLE".equals(estado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No podés marcarte disponible con un pedido en curso");
        }

        conductor.setEstado(Conductor.Estado.valueOf(estado));
        return ConductorResponseDTO.desde(conductorRepository.save(conductor));
    }

    @GetMapping("/pendientes")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<ConductorResponseDTO> listarPendientes() {
        return conductorRepository.findByEstado(Conductor.Estado.PENDIENTE_APROBACION)
                .stream().map(ConductorResponseDTO::desde).collect(Collectors.toList());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<ConductorResponseDTO> listarTodos() {
        return conductorRepository.findAll()
                .stream().map(ConductorResponseDTO::desde).collect(Collectors.toList());
    }

    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ConductorResponseDTO> aprobar(@PathVariable UUID id) {
        Conductor conductor = obtenerPendiente(id);
        conductor.setEstado(Conductor.Estado.DISPONIBLE);
        Conductor guardado = conductorRepository.save(conductor);

        notificar(conductor.getUsuario().getCognitoSub(), "¡Ya sos conductor!",
                "Tu perfil de conductor fue aprobado, ya podés empezar a repartir.");

        return ResponseEntity.ok(ConductorResponseDTO.desde(guardado));
    }

    @PatchMapping("/{id}/rechazar")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ConductorResponseDTO> rechazar(
            @PathVariable UUID id,
            @Valid @RequestBody RechazoConductorRequestDTO request) {

        Conductor conductor = obtenerPendiente(id);
        conductor.setEstado(Conductor.Estado.RECHAZADO);
        conductor.setMotivoRechazo(request.motivo());
        Conductor guardado = conductorRepository.save(conductor);

        notificar(conductor.getUsuario().getCognitoSub(), "Solicitud de conductor rechazada",
                "Motivo: " + request.motivo());

        return ResponseEntity.ok(ConductorResponseDTO.desde(guardado));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        if (!conductorRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conductor no encontrado");
        }
        conductorRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void notificar(String cognitoSub, String titulo, String mensaje) {
        if (cognitoSub == null) return;
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuarioId(UUID.fromString(cognitoSub));
        notificacion.setTitulo(titulo);
        notificacion.setMensaje(mensaje);
        notificacionRepository.save(notificacion);
    }

    private Conductor obtenerPendiente(UUID id) {
        Conductor conductor = conductorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conductor no encontrado"));
        if (conductor.getEstado() != Conductor.Estado.PENDIENTE_APROBACION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya fue resuelta anteriormente");
        }
        return conductor;
    }

    private Usuario obtenerUsuarioActual(JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        String email = jwt.getClaimAsString("email");

        if (email == null) {
            Map<String, Object> userInfo = userInfoClient.obtenerUserInfo(jwt.getTokenValue());
            email = (String) userInfo.get("email");
        }

        final String emailFinal = email;
        return usuarioRepository.findByEmail(emailFinal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }
}