package com.nexo.msusers.controller;

import com.nexo.msusers.client.CognitoUserInfoClient;
import com.nexo.msusers.dto.UsuarioResponseDTO;
import com.nexo.msusers.entity.Usuario;
import com.nexo.msusers.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CognitoUserInfoClient userInfoClient;

    @Mock
    private JwtAuthenticationToken auth;

    @Mock
    private Jwt jwt;

    private UsuarioController controller;

    @BeforeEach
    void setUp() {
        controller = new UsuarioController(usuarioRepository, userInfoClient);
    }

    private Usuario crearUsuarioDePrueba() {
        Usuario usuario = new Usuario();
        usuario.setEmail("test@example.com");
        usuario.setNombre("Test User");
        usuario.setRol(Usuario.Rol.CLIENTE);
        usuario.setEstado(Usuario.Estado.ACTIVO);
        return usuario;
    }

    // ==================== obtenerUsuarioActual (/me) ====================

    @Test
    void obtenerUsuarioActual_jwtConTodosLosClaims_noConsultaUserInfo() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("sub-123");
        when(jwt.getClaimAsString("email")).thenReturn("juan@example.com");
        when(jwt.getClaimAsString("name")).thenReturn("Juan Perez");
        when(jwt.getClaimAsString("picture")).thenReturn("http://foto.com/juan.jpg");

        Usuario existente = crearUsuarioDePrueba();
        existente.setEmail("juan@example.com");
        existente.setCognitoSub("sub-123");

        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(existente));

        ResponseEntity<UsuarioResponseDTO> response = controller.obtenerUsuarioActual(auth);

        assertEquals(200, response.getStatusCode().value());
        verify(userInfoClient, never()).obtenerUserInfo(anyString());
    }

    @Test
    void obtenerUsuarioActual_faltaNombreYFoto_consultaUserInfo() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("sub-456");
        when(jwt.getClaimAsString("email")).thenReturn("maria@example.com");
        when(jwt.getClaimAsString("name")).thenReturn(null);
        when(jwt.getClaimAsString("picture")).thenReturn(null);
        when(jwt.getTokenValue()).thenReturn("token-abc");

        when(userInfoClient.obtenerUserInfo("token-abc")).thenReturn(Map.of(
                "email", "maria@example.com",
                "name", "Maria Lopez",
                "picture", "http://foto.com/maria.jpg"
        ));

        Usuario existente = crearUsuarioDePrueba();
        existente.setEmail("maria@example.com");
        existente.setCognitoSub("sub-456");

        when(usuarioRepository.findByEmail("maria@example.com")).thenReturn(Optional.of(existente));

        controller.obtenerUsuarioActual(auth);

        verify(userInfoClient).obtenerUserInfo("token-abc");
    }

    @Test
    void obtenerUsuarioActual_usuarioNoExiste_loCrea() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("sub-789");
        when(jwt.getClaimAsString("email")).thenReturn("nuevo@example.com");
        when(jwt.getClaimAsString("name")).thenReturn("Nuevo Usuario");
        when(jwt.getClaimAsString("picture")).thenReturn("http://foto.com/nuevo.jpg");

        when(usuarioRepository.findByEmail("nuevo@example.com")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            return u;
        });

        controller.obtenerUsuarioActual(auth);

        verify(usuarioRepository, atLeastOnce()).save(argThat(u ->
                u.getEmail().equals("nuevo@example.com")
                        && u.getNombre().equals("Nuevo Usuario")
                        && u.getRol() == Usuario.Rol.CLIENTE
                        && u.getEstado() == Usuario.Estado.ACTIVO
        ));
    }

    @Test
    void obtenerUsuarioActual_usuarioNuevoSinNombre_usaEmailComoNombre() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("sub-999");
        when(jwt.getClaimAsString("email")).thenReturn("sinnombre@example.com");
        when(jwt.getClaimAsString("name")).thenReturn(null);
        when(jwt.getClaimAsString("picture")).thenReturn(null);
        when(jwt.getTokenValue()).thenReturn("token-xyz");

        when(userInfoClient.obtenerUserInfo("token-xyz")).thenReturn(Map.of(
                "email", "sinnombre@example.com"
        ));

        when(usuarioRepository.findByEmail("sinnombre@example.com")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.obtenerUsuarioActual(auth);

        verify(usuarioRepository).save(argThat(u ->
                u.getNombre().equals("sinnombre@example.com")
        ));
    }

    @Test
    void obtenerUsuarioActual_sinEmailEnNingunLado_lanza400() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("sub-000");
        when(jwt.getClaimAsString("email")).thenReturn(null);
        when(jwt.getClaimAsString("name")).thenReturn(null);
        when(jwt.getClaimAsString("picture")).thenReturn(null);
        when(jwt.getTokenValue()).thenReturn("token-vacio");

        when(userInfoClient.obtenerUserInfo("token-vacio")).thenReturn(Map.of());

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.obtenerUsuarioActual(auth)
        );

        assertEquals(400, excepcion.getStatusCode().value());
    }

    @Test
    void obtenerUsuarioActual_usuarioExistenteSinCognitoSub_loActualiza() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn("sub-nuevo");
        when(jwt.getClaimAsString("email")).thenReturn("viejo@example.com");
        when(jwt.getClaimAsString("name")).thenReturn("Usuario Viejo");
        when(jwt.getClaimAsString("picture")).thenReturn("http://foto.com/viejo.jpg");

        Usuario existente = crearUsuarioDePrueba();
        existente.setEmail("viejo@example.com");
        existente.setCognitoSub(null);

        when(usuarioRepository.findByEmail("viejo@example.com")).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.obtenerUsuarioActual(auth);

        verify(usuarioRepository).save(argThat(u -> "sub-nuevo".equals(u.getCognitoSub())));
    }

    // ==================== actualizarPerfil (PUT /me) ====================

    @Test
    void actualizarPerfil_conNombreValido_actualiza() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("email")).thenReturn("perfil@example.com");

        Usuario existente = crearUsuarioDePrueba();
        existente.setEmail("perfil@example.com");

        when(usuarioRepository.findByEmail("perfil@example.com")).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.actualizarPerfil(Map.of("nombre", "Nombre Nuevo"), auth);

        assertEquals("Nombre Nuevo", existente.getNombre());
    }

    @Test
    void actualizarPerfil_nombreVacio_noLoModifica() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("email")).thenReturn("perfil2@example.com");

        Usuario existente = crearUsuarioDePrueba();
        existente.setEmail("perfil2@example.com");
        existente.setNombre("Nombre Original");

        when(usuarioRepository.findByEmail("perfil2@example.com")).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.actualizarPerfil(Map.of("nombre", "  "), auth);

        assertEquals("Nombre Original", existente.getNombre());
    }

    @Test
    void actualizarPerfil_usuarioNoExiste_lanza404() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("email")).thenReturn("noexiste@example.com");

        when(usuarioRepository.findByEmail("noexiste@example.com")).thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class,
                () -> controller.actualizarPerfil(Map.of("nombre", "X"), auth)
        );
    }

    // ==================== listarTodos ====================

    @Test
    void listarTodos_devuelveTodosLosUsuarios() {
        Usuario u1 = crearUsuarioDePrueba();
        Usuario u2 = crearUsuarioDePrueba();
        u2.setEmail("otro@example.com");

        when(usuarioRepository.findAll()).thenReturn(List.of(u1, u2));

        List<UsuarioResponseDTO> resultado = controller.listarTodos();

        assertEquals(2, resultado.size());
    }

    @Test
    void listarTodos_sinUsuarios_devuelveListaVacia() {
        when(usuarioRepository.findAll()).thenReturn(List.of());

        List<UsuarioResponseDTO> resultado = controller.listarTodos();

        assertTrue(resultado.isEmpty());
    }

    // ==================== obtenerPorId ====================

    @Test
    void obtenerPorId_existe_loDevuelve() {
        UUID id = UUID.randomUUID();
        Usuario usuario = crearUsuarioDePrueba();

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));

        UsuarioResponseDTO resultado = controller.obtenerPorId(id);

        assertNotNull(resultado);
    }

    @Test
    void obtenerPorId_noExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class,
                () -> controller.obtenerPorId(id)
        );
    }

    // ==================== cambiarRol ====================

    @Test
    void cambiarRol_conRolValido_actualizaCorrectamente() {
        UUID id = UUID.randomUUID();
        Usuario usuarioExistente = crearUsuarioDePrueba();

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuarioExistente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<UsuarioResponseDTO> response = controller.cambiarRol(id, Map.of("rol", "ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Usuario.Rol.ADMIN, usuarioExistente.getRol());
    }

    @Test
    void cambiarRol_todosLosRolesValidos_seAceptan() {
        for (Usuario.Rol rol : Usuario.Rol.values()) {
            UUID id = UUID.randomUUID();
            Usuario usuario = crearUsuarioDePrueba();

            when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

            controller.cambiarRol(id, Map.of("rol", rol.name()));

            assertEquals(rol, usuario.getRol());
        }
    }

    @Test
    void cambiarRol_rolEnMinusculas_seConvierteCorrectamente() {
        UUID id = UUID.randomUUID();
        Usuario usuario = crearUsuarioDePrueba();

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.cambiarRol(id, Map.of("rol", "admin"));

        assertEquals(Usuario.Rol.ADMIN, usuario.getRol());
    }

    @Test
    void cambiarRol_usuarioNoExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.cambiarRol(id, Map.of("rol", "ADMIN"))
        );

        assertEquals(404, excepcion.getStatusCode().value());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRol_rolInvalido_lanza400() {
        UUID id = UUID.randomUUID();
        Usuario usuario = crearUsuarioDePrueba();

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.cambiarRol(id, Map.of("rol", "SUPERADMIN"))
        );

        assertEquals(400, excepcion.getStatusCode().value());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRol_rolVacio_lanza400() {
        UUID id = UUID.randomUUID();
        Usuario usuario = crearUsuarioDePrueba();

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));

        assertThrows(
                ResponseStatusException.class,
                () -> controller.cambiarRol(id, Map.of("rol", ""))
        );
    }

    @Test
    void cambiarRol_rolNulo_lanza400() {
        UUID id = UUID.randomUUID();
        Usuario usuario = crearUsuarioDePrueba();

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));

        Map<String, String> requestSinRol = new java.util.HashMap<>();
        requestSinRol.put("rol", null);

        assertThrows(
                ResponseStatusException.class,
                () -> controller.cambiarRol(id, requestSinRol)
        );
    }

    // ==================== eliminar ====================

    @Test
    void eliminar_usuarioExiste_devuelve204() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.existsById(id)).thenReturn(true);

        ResponseEntity<Void> response = controller.eliminar(id);

        assertEquals(204, response.getStatusCode().value());
        verify(usuarioRepository).deleteById(id);
    }

    @Test
    void eliminar_usuarioNoExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.existsById(id)).thenReturn(false);

        assertThrows(
                ResponseStatusException.class,
                () -> controller.eliminar(id)
        );

        verify(usuarioRepository, never()).deleteById(any());
    }
}