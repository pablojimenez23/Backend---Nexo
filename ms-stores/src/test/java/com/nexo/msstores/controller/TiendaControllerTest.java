package com.nexo.msstores.controller;

import com.nexo.msstores.client.NotificacionClient;
import com.nexo.msstores.client.ProductoClient;
import com.nexo.msstores.dto.RechazoRequestDTO;
import com.nexo.msstores.dto.TiendaRequestDTO;
import com.nexo.msstores.dto.TiendaResponseDTO;
import com.nexo.msstores.entity.Tienda;
import com.nexo.msstores.repository.TiendaRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TiendaControllerTest {

    @Mock
    private TiendaRepository tiendaRepository;

    @Mock
    private ProductoClient productoClient;

    @Mock
    private NotificacionClient notificacionClient;

    @Mock
    private JwtAuthenticationToken auth;

    @Mock
    private Jwt jwt;

    private TiendaController controller;

    private static final UUID OWNER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new TiendaController(tiendaRepository, productoClient, notificacionClient);
    }

    private Tienda crearTiendaDePrueba() {
        Tienda tienda = new Tienda();
        tienda.setOwnerId(OWNER_ID);
        tienda.setNombre("Tienda Test");
        tienda.setDireccion("Calle Falsa 123");
        tienda.setHorario("09:00-21:00");
        tienda.setEstado(Tienda.Estado.PENDING);
        tienda.setCategoria(Tienda.CategoriaTienda.RESTAURANTE);
        return tienda;
    }

    private TiendaRequestDTO crearRequestDePrueba() {
        return new TiendaRequestDTO(
                "Tienda Nueva", "Descripcion", "Direccion 456",
                null, null, null, "10:00-22:00", null, "RESTAURANTE"
        );
    }

    // Simula el @PrePersist real de la entidad, que solo corre en persistencia real (no en mocks)
    private Tienda simularPrePersist(Tienda t) {
        if (t.getEstado() == null) t.setEstado(Tienda.Estado.PENDING);
        return t;
    }

    // ==================== listarAprobadas ====================

    @Test
    void listarAprobadas_devuelveSoloAprobadas() {
        Tienda t1 = crearTiendaDePrueba();
        t1.setEstado(Tienda.Estado.APPROVED);

        when(tiendaRepository.findByEstado(Tienda.Estado.APPROVED)).thenReturn(List.of(t1));

        List<TiendaResponseDTO> resultado = controller.listarAprobadas();

        assertEquals(1, resultado.size());
    }

    // ==================== listarPorCategoria ====================

    @Test
    void listarPorCategoria_categoriaValida_filtra() {
        when(tiendaRepository.findByEstadoAndCategoria(Tienda.Estado.APPROVED, Tienda.CategoriaTienda.CAFETERIA))
                .thenReturn(List.of(crearTiendaDePrueba()));

        List<TiendaResponseDTO> resultado = controller.listarPorCategoria("cafeteria");

        assertEquals(1, resultado.size());
    }

    @Test
    void listarPorCategoria_categoriaInvalida_lanzaExcepcion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.listarPorCategoria("INEXISTENTE")
        );
    }

    // ==================== buscar ====================

    @Test
    void buscar_conCoincidencia_devuelveResultados() {
        when(tiendaRepository.findByEstadoAndNombreContainingIgnoreCase(Tienda.Estado.APPROVED, "parrilla"))
                .thenReturn(List.of(crearTiendaDePrueba()));

        List<TiendaResponseDTO> resultado = controller.buscar("parrilla");

        assertEquals(1, resultado.size());
    }

    @Test
    void buscar_sinCoincidencia_devuelveListaVacia() {
        when(tiendaRepository.findByEstadoAndNombreContainingIgnoreCase(Tienda.Estado.APPROVED, "inexistente"))
                .thenReturn(List.of());

        List<TiendaResponseDTO> resultado = controller.buscar("inexistente");

        assertTrue(resultado.isEmpty());
    }

    // ==================== listarTodas ====================

    @Test
    void listarTodas_devuelveTodasSinFiltroDeEstado() {
        Tienda pendiente = crearTiendaDePrueba();
        Tienda aprobada = crearTiendaDePrueba();
        aprobada.setEstado(Tienda.Estado.APPROVED);

        when(tiendaRepository.findAll()).thenReturn(List.of(pendiente, aprobada));

        List<TiendaResponseDTO> resultado = controller.listarTodas();

        assertEquals(2, resultado.size());
    }

    // ==================== obtenerPorId ====================

    @Test
    void obtenerPorId_existe_loDevuelve() {
        UUID id = UUID.randomUUID();
        when(tiendaRepository.findById(id)).thenReturn(Optional.of(crearTiendaDePrueba()));

        TiendaResponseDTO resultado = controller.obtenerPorId(id);

        assertNotNull(resultado);
    }

    @Test
    void obtenerPorId_noExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(tiendaRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.obtenerPorId(id)
        );
        assertEquals(404, excepcion.getStatusCode().value());
    }

    // ==================== solicitarRegistro ====================

    @Test
    void solicitarRegistro_datosValidos_creaConOwnerIdDelJwt() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("sub")).thenReturn(OWNER_ID.toString());
        when(tiendaRepository.save(any(Tienda.class))).thenAnswer(inv -> simularPrePersist(inv.getArgument(0)));

        ResponseEntity<TiendaResponseDTO> response = controller.solicitarRegistro(crearRequestDePrueba(), auth);

        assertEquals(201, response.getStatusCode().value());
        verify(tiendaRepository).save(argThat(t -> OWNER_ID.equals(t.getOwnerId())));
        verify(notificacionClient).enviar(eq(OWNER_ID), anyString(), anyString());
    }

    @Test
    void solicitarRegistro_quedaEnEstadoPendingPorDefecto() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("sub")).thenReturn(OWNER_ID.toString());
        when(tiendaRepository.save(any(Tienda.class))).thenAnswer(inv -> simularPrePersist(inv.getArgument(0)));

        controller.solicitarRegistro(crearRequestDePrueba(), auth);

        verify(tiendaRepository).save(argThat(t -> t.getEstado() == Tienda.Estado.PENDING));
    }

    // ==================== editar ====================

    @Test
    void editar_dueñoReal_actualizaCorrectamente() {
        UUID id = UUID.randomUUID();
        Tienda existente = crearTiendaDePrueba();

        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("sub")).thenReturn(OWNER_ID.toString());
        when(tiendaRepository.findById(id)).thenReturn(Optional.of(existente));
        when(tiendaRepository.save(any(Tienda.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<TiendaResponseDTO> response = controller.editar(id, crearRequestDePrueba(), auth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Tienda Nueva", existente.getNombre());
    }

    @Test
    void editar_noEsElDueño_lanza403() {
        UUID id = UUID.randomUUID();
        UUID otroUsuario = UUID.randomUUID();
        Tienda existente = crearTiendaDePrueba(); // dueño = OWNER_ID

        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("sub")).thenReturn(otroUsuario.toString());
        when(tiendaRepository.findById(id)).thenReturn(Optional.of(existente));

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.editar(id, crearRequestDePrueba(), auth)
        );
        assertEquals(403, excepcion.getStatusCode().value());
        verify(tiendaRepository, never()).save(any());
    }

    @Test
    void editar_tiendaNoExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("sub")).thenReturn(OWNER_ID.toString());
        when(tiendaRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class,
                () -> controller.editar(id, crearRequestDePrueba(), auth)
        );
    }

    // ==================== aprobar ====================

    @Test
    void aprobar_tiendaPendiente_cambiaAAprobadaYNotifica() {
        UUID id = UUID.randomUUID();
        Tienda pendiente = crearTiendaDePrueba();

        when(tiendaRepository.findById(id)).thenReturn(Optional.of(pendiente));
        when(tiendaRepository.save(any(Tienda.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<TiendaResponseDTO> response = controller.aprobar(id);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Tienda.Estado.APPROVED, pendiente.getEstado());
        verify(notificacionClient).enviar(eq(OWNER_ID), anyString(), anyString());
    }

    @Test
    void aprobar_tiendaYaResuelta_lanza409() {
        UUID id = UUID.randomUUID();
        Tienda yaAprobada = crearTiendaDePrueba();
        yaAprobada.setEstado(Tienda.Estado.APPROVED);

        when(tiendaRepository.findById(id)).thenReturn(Optional.of(yaAprobada));

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.aprobar(id)
        );
        assertEquals(409, excepcion.getStatusCode().value());
    }

    @Test
    void aprobar_tiendaNoExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(tiendaRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class,
                () -> controller.aprobar(id)
        );
    }

    // ==================== rechazar ====================

    @Test
    void rechazar_tiendaPendiente_cambiaARechazadaConMotivo() {
        UUID id = UUID.randomUUID();
        Tienda pendiente = crearTiendaDePrueba();
        RechazoRequestDTO request = new RechazoRequestDTO("No cumple requisitos");

        when(tiendaRepository.findById(id)).thenReturn(Optional.of(pendiente));
        when(tiendaRepository.save(any(Tienda.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.rechazar(id, request);

        assertEquals(Tienda.Estado.REJECTED, pendiente.getEstado());
        assertEquals("No cumple requisitos", pendiente.getMotivoRechazo());
        verify(notificacionClient).enviar(eq(OWNER_ID), anyString(), contains("No cumple requisitos"));
    }

    @Test
    void rechazar_tiendaYaResuelta_lanza409() {
        UUID id = UUID.randomUUID();
        Tienda yaRechazada = crearTiendaDePrueba();
        yaRechazada.setEstado(Tienda.Estado.REJECTED);
        RechazoRequestDTO request = new RechazoRequestDTO("Motivo");

        when(tiendaRepository.findById(id)).thenReturn(Optional.of(yaRechazada));

        assertThrows(
                ResponseStatusException.class,
                () -> controller.rechazar(id, request)
        );
    }

    // ==================== eliminar ====================

    @Test
    void eliminar_sinProductos_seElimina() {
        UUID id = UUID.randomUUID();
        when(tiendaRepository.existsById(id)).thenReturn(true);
        when(productoClient.tieneProductos(id)).thenReturn(false);

        ResponseEntity<Void> response = controller.eliminar(id);

        assertEquals(204, response.getStatusCode().value());
        verify(tiendaRepository).deleteById(id);
    }

    @Test
    void eliminar_conProductosAsociados_lanza409() {
        UUID id = UUID.randomUUID();
        when(tiendaRepository.existsById(id)).thenReturn(true);
        when(productoClient.tieneProductos(id)).thenReturn(true);

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.eliminar(id)
        );
        assertEquals(409, excepcion.getStatusCode().value());
        verify(tiendaRepository, never()).deleteById(any());
    }

    @Test
    void eliminar_tiendaNoExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(tiendaRepository.existsById(id)).thenReturn(false);

        assertThrows(
                ResponseStatusException.class,
                () -> controller.eliminar(id)
        );
        verify(productoClient, never()).tieneProductos(any());
    }

    // ==================== misTiendas ====================

    @Test
    void misTiendas_devuelveLasDelSubDelJwt() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("sub")).thenReturn(OWNER_ID.toString());
        when(tiendaRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(crearTiendaDePrueba()));

        List<TiendaResponseDTO> resultado = controller.misTiendas(auth);

        assertEquals(1, resultado.size());
    }

    @Test
    void misTiendas_sinTiendasAsociadas_devuelveListaVacia() {
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("sub")).thenReturn(UUID.randomUUID().toString());
        when(tiendaRepository.findByOwnerId(any())).thenReturn(List.of());

        List<TiendaResponseDTO> resultado = controller.misTiendas(auth);

        assertTrue(resultado.isEmpty());
    }
}