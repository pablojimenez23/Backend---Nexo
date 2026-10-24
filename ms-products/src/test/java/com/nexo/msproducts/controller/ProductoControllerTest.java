package com.nexo.msproducts.controller;

import com.nexo.msproducts.client.TiendaClient;
import com.nexo.msproducts.dto.ProductoRequestDTO;
import com.nexo.msproducts.dto.ProductoResponseDTO;
import com.nexo.msproducts.dto.ReservaStockResponseDTO;
import com.nexo.msproducts.entity.Oferta;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.repository.GrupoOpcionesRepository;
import com.nexo.msproducts.repository.OpcionProductoRepository;
import com.nexo.msproducts.repository.ProductoRepository;
import com.nexo.msproducts.repository.VarianteProductoRepository;
import com.nexo.msproducts.service.CatalogoTipos;
import com.nexo.msproducts.service.OfertaService;
import com.nexo.msproducts.service.ProductoDetalleService;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoControllerTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private TiendaClient tiendaClient;
    @Mock private OfertaService ofertaService;
    @Mock private VarianteProductoRepository varianteRepository;
    @Mock private GrupoOpcionesRepository grupoRepository;
    @Mock private OpcionProductoRepository opcionRepository;
    @Mock private JwtAuthenticationToken auth;
    @Mock private Jwt jwt;

    private ProductoController controller;

    private static final UUID TIENDA_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final String INTERNAL_KEY = "test-internal-key";

    @BeforeEach
    void setUp() {
        ProductoDetalleService detalle =
                new ProductoDetalleService(varianteRepository, grupoRepository, opcionRepository, new CatalogoTipos());
        controller = new ProductoController(productoRepository, tiendaClient, ofertaService, detalle, varianteRepository);
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

    private Oferta ofertaDeTreintaPorCiento() {
        Oferta o = new Oferta();
        o.setTiendaId(TIENDA_ID);
        o.setPorcentaje(30);
        o.setInicio(Instant.now().minusSeconds(60));
        o.setFin(Instant.now().plusSeconds(3600));
        o.setLimitePorPedido(2);
        return o;
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
        assertNull(resultado.get(0).oferta());
    }

    @Test
    void listarPorTienda_sinProductos_devuelveListaVacia() {
        when(productoRepository.findByTiendaId(TIENDA_ID)).thenReturn(List.of());

        List<ProductoResponseDTO> resultado = controller.listarPorTienda(TIENDA_ID);

        assertTrue(resultado.isEmpty());
    }

    @Test
    void listarPorTienda_productoConOferta_incluyeElPrecioDeOferta() {
        UUID productoId = UUID.randomUUID();
        Producto producto = crearProductoDePrueba();
        ReflectionTestUtils.setField(producto, "id", productoId);

        when(productoRepository.findByTiendaId(TIENDA_ID)).thenReturn(List.of(producto));
        when(ofertaService.vigentesPorTienda(eq(TIENDA_ID), any(Instant.class)))
                .thenReturn(Map.of(productoId, ofertaDeTreintaPorCiento()));

        List<ProductoResponseDTO> resultado = controller.listarPorTienda(TIENDA_ID);

        assertNotNull(resultado.get(0).oferta());
        assertEquals(0, resultado.get(0).oferta().precioOferta().compareTo(new BigDecimal("3500")));
    }

    // ==================== obtenerPorId ====================

    @Test
    void obtenerPorId_existe_loDevuelve() {
        UUID id = UUID.randomUUID();
        when(productoRepository.findById(id)).thenReturn(Optional.of(crearProductoDePrueba()));

        ProductoResponseDTO resultado = controller.obtenerPorId(id);

        assertNotNull(resultado);
        assertNull(resultado.oferta());
        assertEquals("OTRO", resultado.tipo());
    }

    @Test
    void obtenerPorId_conOfertaVigente_laIncluye() {
        UUID id = UUID.randomUUID();
        when(productoRepository.findById(id)).thenReturn(Optional.of(crearProductoDePrueba()));
        when(ofertaService.vigenteDe(eq(id), any(Instant.class))).thenReturn(Optional.of(ofertaDeTreintaPorCiento()));

        ProductoResponseDTO resultado = controller.obtenerPorId(id);

        assertEquals(30, resultado.oferta().porcentaje());
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

    @Test
    void crear_precioInvalido_lanza400() {
        simularUsuarioNormal(OWNER_ID);
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);

        ProductoRequestDTO sinPrecio = new ProductoRequestDTO(
                TIENDA_ID, "Producto", "Desc", BigDecimal.ZERO, 5, true
        );

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.crear(sinPrecio, auth)
        );
        assertEquals(400, excepcion.getStatusCode().value());
        verify(productoRepository, never()).save(any());
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
    void eliminar_dueñoReal_seEliminaConSusVariantes() {
        UUID id = UUID.randomUUID();
        Producto existente = crearProductoDePrueba();

        simularUsuarioNormal(OWNER_ID);
        when(productoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);

        ResponseEntity<Void> response = controller.eliminar(id, auth);

        assertEquals(204, response.getStatusCode().value());
        verify(productoRepository).delete(existente);
        verify(varianteRepository).deleteByProductoId(id);
        verify(grupoRepository).deleteByProductoId(id);
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
    void reservarStock_stockSuficiente_descuentaYDevuelveLaOferta() {
        UUID id = UUID.randomUUID();
        UUID ofertaId = UUID.randomUUID();
        Producto producto = crearProductoDePrueba();
        ReservaStockResponseDTO reserva = new ReservaStockResponseDTO(ofertaId, new BigDecimal("3500"), 2);

        when(productoRepository.findById(id)).thenReturn(Optional.of(producto));
        when(productoRepository.reservarStock(id, 3)).thenReturn(1);
        when(ofertaService.aplicarOferta(eq(producto), eq(3), any(Instant.class))).thenReturn(reserva);

        ResponseEntity<ReservaStockResponseDTO> response = controller.reservarStock(id, 3, null, INTERNAL_KEY);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(ofertaId, response.getBody().ofertaId());
        assertEquals(2, response.getBody().unidadesEnOferta());
        verify(productoRepository).reservarStock(id, 3);
    }

    @Test
    void reservarStock_stockInsuficiente_lanza409() {
        UUID id = UUID.randomUUID();
        Producto producto = crearProductoDePrueba();

        when(productoRepository.findById(id)).thenReturn(Optional.of(producto));
        when(productoRepository.reservarStock(id, 50)).thenReturn(0);

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.reservarStock(id, 50, null, INTERNAL_KEY)
        );
        assertEquals(409, excepcion.getStatusCode().value());
        verify(ofertaService, never()).aplicarOferta(any(), anyInt(), any());
    }

    @Test
    void reservarStock_cantidadCero_lanza400() {
        UUID id = UUID.randomUUID();

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.reservarStock(id, 0, null, INTERNAL_KEY)
        );
        assertEquals(400, excepcion.getStatusCode().value());
        verify(productoRepository, never()).reservarStock(any(), anyInt());
    }

    @Test
    void reservarStock_productoNoExiste_lanza404() {
        UUID id = UUID.randomUUID();
        when(productoRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.reservarStock(id, 1, null, INTERNAL_KEY)
        );
        assertEquals(404, excepcion.getStatusCode().value());
    }

    @Test
    void reservarStock_sinKeyValida_lanza403() {
        UUID id = UUID.randomUUID();

        assertThrows(
                ResponseStatusException.class,
                () -> controller.reservarStock(id, 1, null, "key-incorrecta")
        );
        verify(productoRepository, never()).findById(any());
    }

    // ==================== revertirStock ====================

    @Test
    void revertirStock_conKeyValida_sumaStock() {
        UUID id = UUID.randomUUID();

        when(productoRepository.findById(id)).thenReturn(Optional.of(crearProductoDePrueba()));

        controller.revertirStock(id, 5, null, 0, null, INTERNAL_KEY);

        verify(productoRepository).revertirStock(id, 5);
        verify(ofertaService).devolverCupo(null, 0);
    }

    @Test
    void revertirStock_conOferta_devuelveElCupo() {
        UUID id = UUID.randomUUID();
        UUID ofertaId = UUID.randomUUID();

        when(productoRepository.findById(id)).thenReturn(Optional.of(crearProductoDePrueba()));

        controller.revertirStock(id, 3, ofertaId, 2, null, INTERNAL_KEY);

        verify(productoRepository).revertirStock(id, 3);
        verify(ofertaService).devolverCupo(ofertaId, 2);
    }

    @Test
    void revertirStock_sinKeyValida_lanza403() {
        UUID id = UUID.randomUUID();

        assertThrows(
                ResponseStatusException.class,
                () -> controller.revertirStock(id, 1, null, 0, null, "key-incorrecta")
        );
        verify(productoRepository, never()).revertirStock(any(), anyInt());
    }
}