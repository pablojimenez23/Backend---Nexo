package com.nexo.msusers.controller;

import com.nexo.msusers.dto.ConductorRequestDTO;
import com.nexo.msusers.dto.ConductorResponseDTO;
import com.nexo.msusers.entity.Conductor;
import com.nexo.msusers.entity.Usuario;
import com.nexo.msusers.repository.ConductorRepository;
import com.nexo.msusers.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/conductores")
public class ConductorController {

    private final ConductorRepository conductorRepository;
    private final UsuarioRepository usuarioRepository;

    public ConductorController(ConductorRepository conductorRepository, UsuarioRepository usuarioRepository) {
        this.conductorRepository = conductorRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // E5-H2: completar datos de vehículo al registrarse como conductor
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
        return ResponseEntity.status(HttpStatus.CREATED).body(ConductorResponseDTO.desde(guardado));
    }

    // Ver mi propio perfil de conductor
    @GetMapping("/me")
    public ConductorResponseDTO miPerfil(JwtAuthenticationToken auth) {
        Usuario usuario = obtenerUsuarioActual(auth);
        Conductor conductor = conductorRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tenés perfil de conductor"));
        return ConductorResponseDTO.desde(conductor);
    }

    // E5-H3: cambiar disponibilidad (DISPONIBLE, OCUPADO, INACTIVO)
    @PatchMapping("/me/estado")
    public ConductorResponseDTO cambiarEstado(@RequestParam String estado, JwtAuthenticationToken auth) {
        Usuario usuario = obtenerUsuarioActual(auth);
        Conductor conductor = conductorRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tenés perfil de conductor"));

        // E5-H3 escenario 2: no puede pasar a DISPONIBLE si está OCUPADO (pedido en curso)
        if (conductor.getEstado() == Conductor.Estado.OCUPADO && "DISPONIBLE".equals(estado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No podés marcarte disponible con un pedido en curso");
        }

        conductor.setEstado(Conductor.Estado.valueOf(estado));
        return ConductorResponseDTO.desde(conductorRepository.save(conductor));
    }

    private Usuario obtenerUsuarioActual(JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        String email = jwt.getClaimAsString("email");
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }
}