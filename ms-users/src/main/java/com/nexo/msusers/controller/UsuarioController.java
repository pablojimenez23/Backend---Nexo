package com.nexo.msusers.controller;

import com.nexo.msusers.client.CognitoUserInfoClient;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final CognitoUserInfoClient userInfoClient;

    public UsuarioController(UsuarioRepository usuarioRepository, CognitoUserInfoClient userInfoClient) {
        this.usuarioRepository = usuarioRepository;
        this.userInfoClient = userInfoClient;
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> obtenerUsuarioActual(JwtAuthenticationToken auth) {
        Jwt jwt = (Jwt) auth.getPrincipal();
        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String nombre = jwt.getClaimAsString("name");
        String picture = jwt.getClaimAsString("picture");

        if (email == null) {
            Map<String, Object> userInfo = userInfoClient.obtenerUserInfo(jwt.getTokenValue());
            email = (String) userInfo.get("email");
            nombre = (String) userInfo.get("name");
            picture = (String) userInfo.get("picture");
        }

        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pudo obtener el email del usuario autenticado");
        }

        final String emailFinal = email;
        final String nombreFinal = nombre;
        final String pictureFinal = picture;

        Usuario usuario = usuarioRepository.findByEmail(emailFinal)
                .orElseGet(() -> crearNuevoUsuario(emailFinal, nombreFinal, pictureFinal, sub));

        if (usuario.getCognitoSub() == null) {
            usuario.setCognitoSub(sub);
            usuario = usuarioRepository.save(usuario);
        }

        return ResponseEntity.ok(UsuarioResponseDTO.desde(usuario));
    }

    @PutMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> actualizarPerfil(
            @RequestBody Map<String, String> request, JwtAuthenticationToken auth) {

        Jwt jwt = (Jwt) auth.getPrincipal();
        String email = jwt.getClaimAsString("email");
        if (email == null) {
            Map<String, Object> userInfo = userInfoClient.obtenerUserInfo(jwt.getTokenValue());
            email = (String) userInfo.get("email");
        }

        final String emailFinal = email;
        Usuario usuario = usuarioRepository.findByEmail(emailFinal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        String nuevoNombre = request.get("nombre");
        if (nuevoNombre != null && !nuevoNombre.isBlank()) {
            usuario.setNombre(nuevoNombre.trim());
        }

        return ResponseEntity.ok(UsuarioResponseDTO.desde(usuarioRepository.save(usuario)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll()
                .stream().map(UsuarioResponseDTO::desde).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public UsuarioResponseDTO obtenerPorId(@PathVariable UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        return UsuarioResponseDTO.desde(usuario);
    }

    // Cambia el rol de un usuario (CLIENTE, TIENDA, CONDUCTOR, ADMIN). Solo accesible por administradores.
    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UsuarioResponseDTO> cambiarRol(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        String nuevoRol = request.get("rol");
        if (nuevoRol == null || nuevoRol.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debés indicar el nuevo rol");
        }

        try {
            usuario.setRol(Usuario.Rol.valueOf(nuevoRol.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido: " + nuevoRol);
        }

        return ResponseEntity.ok(UsuarioResponseDTO.desde(usuarioRepository.save(usuario)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        if (!usuarioRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }
        usuarioRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Usuario crearNuevoUsuario(String email, String nombre, String picture, String cognitoSub) {
        Usuario nuevo = new Usuario();
        nuevo.setEmail(email);
        nuevo.setNombre(nombre != null ? nombre : email);
        nuevo.setPictureUrl(picture);
        nuevo.setCognitoSub(cognitoSub);
        nuevo.setRol(Usuario.Rol.CLIENTE);
        nuevo.setEstado(Usuario.Estado.ACTIVO);
        return usuarioRepository.save(nuevo);
    }
}