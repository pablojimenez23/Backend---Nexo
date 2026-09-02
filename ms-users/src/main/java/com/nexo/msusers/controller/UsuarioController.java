package com.nexo.msusers.controller;

import com.nexo.msusers.dto.UsuarioResponseDTO;
import com.nexo.msusers.entity.Usuario;
import com.nexo.msusers.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseGet(() -> crearNuevoUsuario(email, nombre));

        return ResponseEntity.ok(UsuarioResponseDTO.desde(usuario));
    }

    private Usuario crearNuevoUsuario(String email, String nombre) {
        Usuario nuevo = new Usuario();
        nuevo.setEmail(email);
        nuevo.setNombre(nombre != null ? nombre : email);
        nuevo.setRol(Usuario.Rol.CLIENTE);
        nuevo.setEstado(Usuario.Estado.ACTIVO);
        return usuarioRepository.save(nuevo);
    }
}