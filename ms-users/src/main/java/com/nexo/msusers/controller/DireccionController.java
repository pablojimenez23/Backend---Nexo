package com.nexo.msusers.controller;

import com.nexo.msusers.dto.DireccionRequestDTO;
import com.nexo.msusers.dto.DireccionResponseDTO;
import com.nexo.msusers.entity.Direccion;
import com.nexo.msusers.entity.Usuario;
import com.nexo.msusers.repository.DireccionRepository;
import com.nexo.msusers.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/direcciones")
public class DireccionController {

    private final DireccionRepository direccionRepository;
    private final UsuarioRepository usuarioRepository;

    public DireccionController(DireccionRepository direccionRepository, UsuarioRepository usuarioRepository) {
        this.direccionRepository = direccionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // E2-H9: guardar una dirección de envío en la cuenta
    @PostMapping
    public ResponseEntity<DireccionResponseDTO> crear(
            @RequestBody DireccionRequestDTO request, JwtAuthenticationToken auth) {

        Usuario usuario = obtenerUsuarioActual(auth);

        Direccion direccion = new Direccion();
        direccion.setUsuario(usuario);
        direccion.setCalle(request.calle());
        direccion.setCiudad(request.ciudad());
        direccion.setRegion(request.region());
        direccion.setCodigoPostal(request.codigoPostal());
        direccion.setTipo(Direccion.Tipo.valueOf(request.tipo() != null ? request.tipo() : "CASA"));
        direccion.setLatitud(request.latitud());
        direccion.setLongitud(request.longitud());

        Direccion guardada = direccionRepository.save(direccion);
        return ResponseEntity.status(HttpStatus.CREATED).body(DireccionResponseDTO.desde(guardada));
    }

    // Listar mis direcciones guardadas
    @GetMapping
    public List<DireccionResponseDTO> misDirecciones(JwtAuthenticationToken auth) {
        Usuario usuario = obtenerUsuarioActual(auth);
        return direccionRepository.findByUsuarioId(usuario.getId())
                .stream().map(DireccionResponseDTO::desde).collect(Collectors.toList());
    }

    // E2-H10: editar una dirección propia
    @PutMapping("/{id}")
    public DireccionResponseDTO editar(
            @PathVariable UUID id, @RequestBody DireccionRequestDTO request, JwtAuthenticationToken auth) {

        Direccion direccion = buscarPropia(id, auth);
        direccion.setCalle(request.calle());
        direccion.setCiudad(request.ciudad());
        direccion.setRegion(request.region());
        direccion.setCodigoPostal(request.codigoPostal());
        if (request.tipo() != null) {
            direccion.setTipo(Direccion.Tipo.valueOf(request.tipo()));
        }
        direccion.setLatitud(request.latitud());
        direccion.setLongitud(request.longitud());
        return DireccionResponseDTO.desde(direccionRepository.save(direccion));
    }

    // E2-H10: eliminar una dirección propia
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, JwtAuthenticationToken auth) {
        Direccion direccion = buscarPropia(id, auth);
        direccionRepository.delete(direccion);
        return ResponseEntity.noContent().build();
    }

    private Direccion buscarPropia(UUID id, JwtAuthenticationToken auth) {
        Usuario usuario = obtenerUsuarioActual(auth);
        Direccion direccion = direccionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dirección no encontrada"));
        if (!direccion.getUsuario().getId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenés acceso a esta dirección");
        }
        return direccion;
    }

    private Usuario obtenerUsuarioActual(JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        String email = jwt.getClaimAsString("email");
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }
}