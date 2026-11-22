package com.nexo.msproducts.controller;

import com.nexo.msproducts.client.TiendaClient;
import com.nexo.msproducts.dto.ProductoRequestDTO;
import com.nexo.msproducts.dto.ProductoResponseDTO;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoControllerTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private TiendaClient tiendaClient;
    @Mock private JwtAuthenticationToken auth;
    @Mock private Jwt jwt;

    private ProductoController controller;

    private static final UUID TIENDA_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final String INTERNAL_KEY = "test-internal-key";

    @BeforeEach
    void setUp() {
        controller = new ProductoController(productoRepository, tiendaClient);
        ReflectionTestUtils.setField(controller, "internalApiKey", INTERNAL_KEY);
        lenient().when(auth.getPrincipal()).thenReturn(jwt);
    }

    private Producto crearProductoDePrueba() {
        Producto producto = new Producto();
        producto.setTiendaId(TIENDA_ID);
        producto.setNombre("Producto Test");
        producto.setPrecio(new BigDecimal("5000"));
        producto.setStock(10);
        producto.setDisponible(true);
        return producto;
    }

    private ProductoRequestDTO crearRequestDePrueba() {
        return new ProductoRequestDTO(TIENDA_ID, "Producto Nuevo", "Descripcion", new BigDecimal("3000"), 20, true);
    }

    private void simularUsuarioNormal(UUID sub) {
        lenient().when(jwt.getClaimAsString("sub")).thenReturn(sub.toString());
        lenient().when(jwt.getClaimAsStringList("cognito:groups")).thenReturn(List.of("TIENDA"));
    }

    private void simularAdmin() {
        lenient().when(jwt.getClaimAsString("sub")).thenReturn(UUID.randomUUID().toString());
        lenient().when(jwt.getClaimAsStringList("cognito:groups")).thenReturn(List.of("ADMIN"));
    }

    // ==================== listarPorTienda ====================

    @Test
    void listarPorTienda_devuelveLosDeLaTienda() {
        when(productoRepository.findByTiendaId(TIENDA_ID)).thenReturn(List.of(crearProductoDePrueba()));

        List<ProductoResponseDTO> resultado = controller.listarPorTienda(TIENDA_ID);

        assertEquals(1, resultado.size());
    }

    @Test
    void listarPorTienda_sinProductos_devuelveListaVacia() {
        when(productoRepository.findByTiendaId(TIENDA_ID)).thenReturn(List.of());

        List<ProductoResponseDTO> resultado = controller.listarPorTienda(TIENDA_ID);

        assertTrue(resultado.isEmpty());
    }

    // ==================== obtenerPorId ====================

    @Test
    void obtenerPorId_existe_loDevuelve() {
        UUID id = UUID.randomUUID();
        when(productoRepository.findById(id)).thenReturn(Optional.of(crearProductoDePrueba()));

        assertNotNull(controller.obtenerPorId(id));
    }

    @Test
    void obtenerPorId_noExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(productoRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> controller.obtenerPorId(id));
    }

    // ==================== crear ====================

    @Test
    void crear_dueñoReal_creaCorrectamente() {
        simularUsuarioNormal(OWNER_ID);
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<ProductoResponseDTO> response = controller.crear(crearRequestDePrueba(), auth);

        assertEquals(201, response.getStatusCode().value());
        verify(productoRepository).save(argThat(p -> p.getNombre().equals("Producto Nuevo")));
    }

    @Test
    void crear_noEsElDueño_lanza403() {
        UUID otroUsuario = UUID.randomUUID();
        simularUsuarioNormal(otroUsuario);
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.crear(crearRequestDePrueba(), auth)
        );
        assertEquals(403, excepcion.getStatusCode().value());
        verify(productoRepository, never()).save(any());
    }

    @Test
    void crear_comoAdmin_noVerificaDueño() {
        simularAdmin();

        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<ProductoResponseDTO> response = controller.crear(crearRequestDePrueba(), auth);

        assertEquals(201, response.getStatusCode().value());
        verify(tiendaClient, never()).obtenerOwnerId(any());
    }

    @Test
    void crear_stockNulo_seGuardaComoCero() {
        simularUsuarioNormal(OWNER_ID);
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoRequestDTO requestSinStock = new ProductoRequestDTO(
                TIENDA_ID, "Producto", "Desc", new BigDecimal("1000"), null, null
        );

        controller.crear(requestSinStock, auth);

        verify(productoRepository).save(argThat(p -> p.getStock() == 0 && p.isDisponible()));
    }

    // ==================== editar ====================

    @Test
    void editar_dueñoReal_actualizaCorrectamente() {
        UUID id = UUID.randomUUID();
        Producto existente = crearProductoDePrueba();

        simularUsuarioNormal(OWNER_ID);
        when(productoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.editar(id, crearRequestDePrueba(), auth);

        assertEquals("Producto Nuevo", existente.getNombre());
        assertEquals(0, existente.getStock().compareTo(20));
    }

    @Test
    void editar_noEsElDueño_lanza403() {
        UUID id = UUID.randomUUID();
        UUID otroUsuario = UUID.randomUUID();
        Producto existente = crearProductoDePrueba();

        simularUsuarioNormal(otroUsuario);
        when(productoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);

        assertThrows(ResponseStatusException.class, () -> controller.editar(id, crearRequestDePrueba(), auth));
        verify(productoRepository, never()).save(any());
    }

    @Test
    void editar_productoNoExiste_lanza404() {
        UUID id = UUID.randomUUID();
        simularUsuarioNormal(OWNER_ID);
        when(productoRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> controller.editar(id, crearRequestDePrueba(), auth));
    }

    // ==================== cambiarDisponibilidad ====================

    @Test
    void cambiarDisponibilidad_productoExiste_actualiza() {
        UUID id = UUID.randomUUID();
        Producto existente = crearProductoDePrueba();

        when(productoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.cambiarDisponibilidad(id, false);

        assertFalse(existente.isDisponible());
    }

    @Test
    void cambiarDisponibilidad_productoNoExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(productoRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> controller.cambiarDisponibilidad(id, true));
    }

    // ==================== eliminar ====================

    @Test
    void eliminar_dueñoReal_seElimina() {
        UUID id = UUID.randomUUID();
        Producto existente = crearProductoDePrueba();

        simularUsuarioNormal(OWNER_ID);
        when(productoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);

        ResponseEntity<Void> response = controller.eliminar(id, auth);

        assertEquals(204, response.getStatusCode().value());
        verify(productoRepository).delete(existente);
    }

    @Test
    void eliminar_noEsElDueño_lanza403() {
        UUID id = UUID.randomUUID();
        UUID otroUsuario = UUID.randomUUID();
        Producto existente = crearProductoDePrueba();

        simularUsuarioNormal(otroUsuario);
        when(productoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);

        assertThrows(ResponseStatusException.class, () -> controller.eliminar(id, auth));
        verify(productoRepository, never()).delete(any());
    }

    // ==================== eliminarPorTienda (interno) ====================

    @Test
    void eliminarPorTienda_conKeyValida_eliminaTodos() {
        when(productoRepository.findByTiendaId(TIENDA_ID)).thenReturn(List.of(crearProductoDePrueba(), crearProductoDePrueba()));

        ResponseEntity<Void> response = controller.eliminarPorTienda(TIENDA_ID, INTERNAL_KEY);

        assertEquals(204, response.getStatusCode().value());
        verify(productoRepository).deleteAll(anyList());
    }

    @Test
    void eliminarPorTienda_sinKeyValida_lanza403() {
        assertThrows(
                ResponseStatusException.class,
                () -> controller.eliminarPorTienda(TIENDA_ID, "key-incorrecta")
        );
        verify(productoRepository, never()).deleteAll(anyList());
    }

    // ==================== reservarStock ====================

    @Test
    void reservarStock_stockSuficiente_descuenta() {
        UUID id = UUID.randomUUID();
        Producto producto = crearProductoDePrueba(); // stock = 10

        when(productoRepository.findById(id)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Void> response = controller.reservarStock(id, 3, INTERNAL_KEY);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(7, producto.getStock());
    }

    @Test
    void reservarStock_stockInsuficiente_lanza409() {
        UUID id = UUID.randomUUID();
        Producto producto = crearProductoDePrueba(); // stock = 10

        when(productoRepository.findById(id)).thenReturn(Optional.of(producto));

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.reservarStock(id, 50, INTERNAL_KEY)
        );
        assertEquals(409, excepcion.getStatusCode().value());
        verify(productoRepository, never()).save(any());
    }

    @Test
    void reservarStock_sinKeyValida_lanza403() {
        UUID id = UUID.randomUUID();

        assertThrows(
                ResponseStatusException.class,
                () -> controller.reservarStock(id, 1, "key-incorrecta")
        );
        verify(productoRepository, never()).findById(any());
    }

    // ==================== revertirStock ====================

    @Test
    void revertirStock_conKeyValida_sumaStock() {
        UUID id = UUID.randomUUID();
        Producto producto = crearProductoDePrueba(); // stock = 10

        when(productoRepository.findById(id)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.revertirStock(id, 5, INTERNAL_KEY);

        assertEquals(15, producto.getStock());
    }

    @Test
    void revertirStock_sinKeyValida_lanza403() {
        UUID id = UUID.randomUUID();

        assertThrows(
                ResponseStatusException.class,
                () -> controller.revertirStock(id, 1, "key-incorrecta")
        );
    }
}