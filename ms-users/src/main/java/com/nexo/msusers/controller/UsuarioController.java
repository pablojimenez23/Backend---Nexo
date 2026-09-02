package com.nexo.msusers.controller;

import com.nexo.msusers.dto.UsuarioResponseDTO;
import com.nexo.msusers.entity.Usuario;
import com.nexo.msusers.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;

    public UsuarioController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> obtenerUsuarioActual(JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        String email = jwt.getClaimAsString("email");
        String nombre = jwt.getClaimAsString("name");
        String picture = jwt.getClaimAsString("picture");

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseGet(() -> crearNuevoUsuario(email, nombre, picture));

        return ResponseEntity.ok(UsuarioResponseDTO.desde(usuario));
    }

    // Solo ADMIN: listado completo de cuentas registradas
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll()
                .stream().map(UsuarioResponseDTO::desde).collect(Collectors.toList());
    }

    // Solo ADMIN: elimina el registro local. La cuenta de Google/Cognito no se ve afectada
    // y el usuario se vuelve a crear automáticamente si inicia sesión de nuevo.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        if (!usuarioRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }
        usuarioRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Usuario crearNuevoUsuario(String email, String nombre, String picture) {
        Usuario nuevo = new Usuario();
        nuevo.setEmail(email);
        nuevo.setNombre(nombre != null ? nombre : email);
        nuevo.setPictureUrl(picture);
        nuevo.setRol(Usuario.Rol.CLIENTE);
        nuevo.setEstado(Usuario.Estado.ACTIVO);
        return usuarioRepository.save(nuevo);
    }
}