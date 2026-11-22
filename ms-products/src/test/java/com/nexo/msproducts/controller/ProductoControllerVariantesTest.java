package com.nexo.msproducts.controller;

import com.nexo.msproducts.client.TiendaClient;
import com.nexo.msproducts.dto.GrupoOpcionesDTO;
import com.nexo.msproducts.dto.OpcionDTO;
import com.nexo.msproducts.dto.ProductoRequestDTO;
import com.nexo.msproducts.dto.ProductoResponseDTO;
import com.nexo.msproducts.dto.ReservaStockResponseDTO;
import com.nexo.msproducts.dto.VarianteDTO;
import com.nexo.msproducts.entity.GrupoOpciones;
import com.nexo.msproducts.entity.OpcionProducto;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.entity.TipoProducto;
import com.nexo.msproducts.entity.VarianteProducto;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoControllerVariantesTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private TiendaClient tiendaClient;
    @Mock private OfertaService ofertaService;
    @Mock private VarianteProductoRepository varianteRepository;
    @Mock private GrupoOpcionesRepository grupoRepository;
    @Mock private OpcionProductoRepository opcionRepository;
    @Mock private JwtAuthenticationToken auth;
    @Mock private Jwt jwt;

    @Captor private ArgumentCaptor<List<VarianteProducto>> variantesCaptor;
    @Captor private ArgumentCaptor<List<OpcionProducto>> opcionesCaptor;

    private ProductoController controller;

    private static final UUID TIENDA_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID PRODUCTO_ID = UUID.randomUUID();
    private static final String INTERNAL_KEY = "test-internal-key";

    @BeforeEach
    void setUp() {
        ProductoDetalleService detalle = new ProductoDetalleService(
                varianteRepository, grupoRepository, opcionRepository, new CatalogoTipos());
        controller = new ProductoController(productoRepository, tiendaClient, ofertaService, detalle, varianteRepository);
        ReflectionTestUtils.setField(controller, "internalApiKey", INTERNAL_KEY);
        lenient().when(auth.getPrincipal()).thenReturn(jwt);
        lenient().when(jwt.getClaimAsString("sub")).thenReturn(OWNER_ID.toString());
        lenient().when(jwt.getClaimAsStringList("cognito:groups")).thenReturn(List.of("TIENDA"));
    }

    private Producto productoSimple() {
        Producto p = new Producto();
        p.setTiendaId(TIENDA_ID);
        p.setNombre("Pizza Test");
        p.setPrecio(new BigDecimal("5000"));
        p.setStock(10);
        p.setDisponible(true);
        ReflectionTestUtils.setField(p, "id", PRODUCTO_ID);
        return p;
    }

    private VarianteProducto variante(UUID id, String nombre, String precio, int stock) {
        VarianteProducto v = new VarianteProducto();
        ReflectionTestUtils.setField(v, "id", id);
        v.setProductoId(PRODUCTO_ID);
        v.setNombre(nombre);
        v.setPrecio(new BigDecimal(precio));
        v.setStock(stock);
        return v;
    }

    private GrupoOpciones grupo(UUID id, String nombre, int minimo, int maximo) {
        GrupoOpciones g = new GrupoOpciones();
        ReflectionTestUtils.setField(g, "id", id);
        g.setProductoId(PRODUCTO_ID);
        g.setNombre(nombre);
        g.setMinimo(minimo);
        g.setMaximo(maximo);
        return g;
    }

    private OpcionProducto opcion(UUID id, UUID grupoId, String nombre, String adicional) {
        OpcionProducto o = new OpcionProducto();
        ReflectionTestUtils.setField(o, "id", id);
        o.setGrupoId(grupoId);
        o.setNombre(nombre);
        o.setPrecioAdicional(new BigDecimal(adicional));
        return o;
    }

    private List<VarianteDTO> tresTamanos() {
        return List.of(
                new VarianteDTO(null, "Personal", new BigDecimal("6000"), 5, true),
                new VarianteDTO(null, "Mediana", new BigDecimal("9000"), 8, true),
                new VarianteDTO(null, "Familiar", new BigDecimal("12000"), 3, true));
    }

    private OpcionDTO op(String nombre, String adicional) {
        return new OpcionDTO(null, nombre, new BigDecimal(adicional), true);
    }

    private List<GrupoOpcionesDTO> masaYExtras() {
        return List.of(
                new GrupoOpcionesDTO(null, "Masa", 1, 1, List.of(op("Delgada", "0"), op("Gruesa", "0"))),
                new GrupoOpcionesDTO(null, "Extras", 0, 3, List.of(op("Queso extra", "1000"))));
    }

    private ProductoRequestDTO requestPizza(List<VarianteDTO> variantes, Map<String, Object> atributos) {
        return requestPizza(variantes, atributos, masaYExtras());
    }

    private ProductoRequestDTO requestPizza(List<VarianteDTO> variantes, Map<String, Object> atributos,
                                            List<GrupoOpcionesDTO> grupos) {
        return new ProductoRequestDTO(TIENDA_ID, "Pizza Napolitana", "Con albahaca", null, null, true,
                "PIZZA", atributos, variantes, grupos);
    }

    private Map<String, Object> ingredientesValidos() {
        return Map.of("ingredientes", List.of("Tomate", "Mozzarella", "Albahaca"));
    }

    private void guardarAsignandoId() {
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", PRODUCTO_ID);
            return p;
        });
    }

    // ==================== catálogo ====================

    @Test
    void tipos_devuelveElCatalogoDeProductosDeDelivery() {
        var tipos = controller.tipos();

        assertTrue(tipos.stream().anyMatch(t -> t.clave().equals("PIZZA")));
        assertTrue(tipos.stream().anyMatch(t -> t.clave().equals("SUSHI")));
        assertTrue(tipos.stream().noneMatch(t -> t.clave().equals("VINO")));
    }

    // ==================== listar ====================

    @Test
    void listarPorTienda_conVariantes_muestraElPrecioDesdeYElStockTotal() {
        Producto producto = productoSimple();
        when(productoRepository.findByTiendaId(TIENDA_ID)).thenReturn(List.of(producto));
        when(varianteRepository.findByProductoIdInOrderByOrdenAsc(anyCollection())).thenReturn(List.of(
                variante(UUID.randomUUID(), "Personal", "6000", 5),
                variante(UUID.randomUUID(), "Familiar", "12000", 3)));

        ProductoResponseDTO resultado = controller.listarPorTienda(TIENDA_ID).get(0);

        assertEquals(0, resultado.precio().compareTo(new BigDecimal("6000")));
        assertEquals(8, resultado.stock());
        assertEquals(2, resultado.variantes().size());
    }

    @Test
    void listarPorTienda_conGrupos_losEntregaConSusOpciones() {
        Producto producto = productoSimple();
        UUID masaId = UUID.randomUUID();
        when(productoRepository.findByTiendaId(TIENDA_ID)).thenReturn(List.of(producto));
        when(grupoRepository.findByProductoIdInOrderByOrdenAsc(anyCollection()))
                .thenReturn(List.of(grupo(masaId, "Masa", 1, 1)));
        when(opcionRepository.findByGrupoIdInOrderByOrdenAsc(anyCollection())).thenReturn(List.of(
                opcion(UUID.randomUUID(), masaId, "Delgada", "0"),
                opcion(UUID.randomUUID(), masaId, "Gruesa", "500")));

        ProductoResponseDTO resultado = controller.listarPorTienda(TIENDA_ID).get(0);

        assertEquals(1, resultado.grupos().size());
        assertEquals("Masa", resultado.grupos().get(0).nombre());
        assertTrue(resultado.grupos().get(0).obligatorio());
        assertEquals(2, resultado.grupos().get(0).opciones().size());
    }

    // ==================== crear ====================

    @Test
    void crear_pizzaConVariantesYGrupos_calculaPrecioDesdeYStockTotal() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        guardarAsignandoId();

        controller.crear(requestPizza(tresTamanos(), ingredientesValidos()), auth);

        verify(productoRepository).save(argThat(p ->
                p.getPrecio().compareTo(new BigDecimal("6000")) == 0
                        && p.getStock() == 16
                        && p.getTipo() == TipoProducto.PIZZA
                        && p.getAtributos().contains("Mozzarella")));
        verify(varianteRepository).saveAll(variantesCaptor.capture());
        assertEquals(3, variantesCaptor.getValue().size());
        assertEquals(PRODUCTO_ID, variantesCaptor.getValue().get(0).getProductoId());

        verify(grupoRepository, times(2)).save(any(GrupoOpciones.class));
        verify(opcionRepository).saveAll(opcionesCaptor.capture());
        assertEquals(3, opcionesCaptor.getValue().size());
        assertEquals("Delgada", opcionesCaptor.getValue().get(0).getNombre());
    }

    @Test
    void crear_pizzaSinIngredientes_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.crear(requestPizza(tresTamanos(), Map.of()), auth));

        assertEquals(400, e.getStatusCode().value());
        verify(productoRepository, never()).save(any());
    }

    @Test
    void crear_tipoDesconocido_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        ProductoRequestDTO request = new ProductoRequestDTO(TIENDA_ID, "Algo", "d", new BigDecimal("1000"), 1, true,
                "NO_EXISTE", null, null, null);

        assertThrows(ResponseStatusException.class, () -> controller.crear(request, auth));
        verify(productoRepository, never()).save(any());
    }

    @Test
    void crear_variantesConNombreRepetido_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        List<VarianteDTO> repetidas = List.of(
                new VarianteDTO(null, "Mediana", new BigDecimal("9000"), 5, true),
                new VarianteDTO(null, " mediana ", new BigDecimal("9500"), 5, true));

        assertThrows(ResponseStatusException.class,
                () -> controller.crear(requestPizza(repetidas, ingredientesValidos()), auth));
        verify(productoRepository, never()).save(any());
    }

    @Test
    void crear_masDeOchoVariantes_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        List<VarianteDTO> muchas = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            muchas.add(new VarianteDTO(null, "Opción " + i, new BigDecimal("1000"), 1, true));
        }

        assertThrows(ResponseStatusException.class,
                () -> controller.crear(requestPizza(muchas, ingredientesValidos()), auth));
    }

    @Test
    void crear_varianteConPrecioCero_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        List<VarianteDTO> invalida = List.of(new VarianteDTO(null, "Gratis", BigDecimal.ZERO, 5, true));

        assertThrows(ResponseStatusException.class,
                () -> controller.crear(requestPizza(invalida, ingredientesValidos()), auth));
    }

    @Test
    void crear_bebidaConAlcohol_guardaLosAtributosYExigeMayoriaDeEdad() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        guardarAsignandoId();
        ProductoRequestDTO request = new ProductoRequestDTO(TIENDA_ID, "Cerveza artesanal", "IPA de la casa",
                new BigDecimal("3990"), 12, true, "BEBIDA",
                Map.of("contiene_alcohol", true, "volumen_ml", 500, "grado_alcoholico", 5.5), null, null);

        ResponseEntity<ProductoResponseDTO> response = controller.crear(request, auth);

        verify(productoRepository).save(argThat(p ->
                p.getTipo() == TipoProducto.BEBIDA && p.getAtributos().contains("contiene_alcohol")));
        assertTrue(response.getBody().requiereMayorEdad());
    }

    @Test
    void crear_marcaDestacadoYPersonas() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        guardarAsignandoId();
        ProductoRequestDTO request = new ProductoRequestDTO(TIENDA_ID, "Pizza Grande", "Para compartir",
                new BigDecimal("9000"), 5, true, "PIZZA", ingredientesValidos(), null, null, true, 2);

        controller.crear(request, auth);

        verify(productoRepository).save(argThat(p -> p.isDestacado() && p.getPersonas() == 2));
    }

    @Test
    void crear_personasFueraDeRango_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        ProductoRequestDTO request = new ProductoRequestDTO(TIENDA_ID, "Pizza Grande", "d",
                new BigDecimal("9000"), 5, true, "PIZZA", ingredientesValidos(), null, null, false, 99);

        assertThrows(ResponseStatusException.class, () -> controller.crear(request, auth));
        verify(productoRepository, never()).save(any());
    }

    // ---------- validación de grupos ----------

    @Test
    void crear_grupoSinOpciones_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        List<GrupoOpcionesDTO> vacio = List.of(new GrupoOpcionesDTO(null, "Masa", 1, 1, List.of()));

        assertThrows(ResponseStatusException.class,
                () -> controller.crear(requestPizza(tresTamanos(), ingredientesValidos(), vacio), auth));
        verify(productoRepository, never()).save(any());
    }

    @Test
    void crear_minimoMayorQueLasOpciones_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        List<GrupoOpcionesDTO> imposible = List.of(
                new GrupoOpcionesDTO(null, "Masa", 3, 3, List.of(op("Delgada", "0"), op("Gruesa", "0"))));

        assertThrows(ResponseStatusException.class,
                () -> controller.crear(requestPizza(tresTamanos(), ingredientesValidos(), imposible), auth));
    }

    @Test
    void crear_maximoMenorQueElMinimo_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        List<GrupoOpcionesDTO> invertido = List.of(
                new GrupoOpcionesDTO(null, "Extras", 2, 1, List.of(op("A", "0"), op("B", "0"))));

        assertThrows(ResponseStatusException.class,
                () -> controller.crear(requestPizza(tresTamanos(), ingredientesValidos(), invertido), auth));
    }

    @Test
    void crear_gruposConNombreRepetido_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        List<GrupoOpcionesDTO> repetidos = List.of(
                new GrupoOpcionesDTO(null, "Masa", 1, 1, List.of(op("Delgada", "0"))),
                new GrupoOpcionesDTO(null, " masa ", 0, 1, List.of(op("Gruesa", "0"))));

        assertThrows(ResponseStatusException.class,
                () -> controller.crear(requestPizza(tresTamanos(), ingredientesValidos(), repetidos), auth));
    }

    @Test
    void crear_opcionConPrecioAdicionalNegativo_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        List<GrupoOpcionesDTO> negativo = List.of(
                new GrupoOpcionesDTO(null, "Extras", 0, 2, List.of(op("Descuento raro", "-500"))));

        assertThrows(ResponseStatusException.class,
                () -> controller.crear(requestPizza(tresTamanos(), ingredientesValidos(), negativo), auth));
    }

    @Test
    void crear_masDeOchoGrupos_lanza400() {
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        List<GrupoOpcionesDTO> muchos = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            muchos.add(new GrupoOpcionesDTO(null, "Grupo " + i, 0, 1, List.of(op("A", "0"))));
        }

        assertThrows(ResponseStatusException.class,
                () -> controller.crear(requestPizza(tresTamanos(), ingredientesValidos(), muchos), auth));
    }

    // ==================== editar ====================

    @Test
    void editar_appAntiguaSinVariantes_conservaPrecioYStockDelProductoConVariantes() {
        Producto existente = productoSimple();
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(varianteRepository.existsByProductoId(PRODUCTO_ID)).thenReturn(true);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoRequestDTO request = new ProductoRequestDTO(TIENDA_ID, "Pizza Renombrada", "d",
                new BigDecimal("1"), 999, true);
        controller.editar(PRODUCTO_ID, request, auth);

        assertEquals("Pizza Renombrada", existente.getNombre());
        assertEquals(0, existente.getPrecio().compareTo(new BigDecimal("5000")));
        assertEquals(10, existente.getStock());
        verify(varianteRepository, never()).saveAll(any());
        verify(grupoRepository, never()).save(any());
    }

    @Test
    void editar_actualizaYBorraVariantes() {
        Producto existente = productoSimple();
        UUID personalId = UUID.randomUUID();
        UUID medianaId = UUID.randomUUID();
        VarianteProducto personal = variante(personalId, "Personal", "6000", 5);
        VarianteProducto mediana = variante(medianaId, "Mediana", "9000", 8);

        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(varianteRepository.findByProductoIdOrderByOrdenAsc(PRODUCTO_ID)).thenReturn(List.of(personal, mediana));

        // Se queda "Personal" con otro precio y se borra "Mediana"
        List<VarianteDTO> nuevas = List.of(new VarianteDTO(personalId, "Personal", new BigDecimal("6500"), 4, true));
        controller.editar(PRODUCTO_ID, requestPizza(nuevas, ingredientesValidos()), auth);

        assertEquals(0, personal.getPrecio().compareTo(new BigDecimal("6500")));
        assertEquals(4, personal.getStock());
        verify(varianteRepository).deleteAll(argThat(l -> {
            List<VarianteProducto> lista = new ArrayList<>();
            l.forEach(lista::add);
            return lista.size() == 1 && lista.get(0) == mediana;
        }));
        assertEquals(0, existente.getPrecio().compareTo(new BigDecimal("6500")));
        assertEquals(4, existente.getStock());
    }

    @Test
    void editar_varianteInexistente_lanza400() {
        Producto existente = productoSimple();
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        List<VarianteDTO> conIdAjeno = List.of(
                new VarianteDTO(UUID.randomUUID(), "Fantasma", new BigDecimal("6500"), 4, true));

        assertThrows(ResponseStatusException.class,
                () -> controller.editar(PRODUCTO_ID, requestPizza(conIdAjeno, ingredientesValidos()), auth));
    }

    @Test
    void editar_actualizaUnGrupoYBorraLaOpcionQueSeQuito() {
        Producto existente = productoSimple();
        UUID masaId = UUID.randomUUID();
        UUID delgadaId = UUID.randomUUID();
        UUID gruesaId = UUID.randomUUID();
        GrupoOpciones masa = grupo(masaId, "Masa", 1, 1);
        OpcionProducto delgada = opcion(delgadaId, masaId, "Delgada", "0");
        OpcionProducto gruesa = opcion(gruesaId, masaId, "Gruesa", "0");

        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(grupoRepository.findByProductoIdOrderByOrdenAsc(PRODUCTO_ID)).thenReturn(List.of(masa));
        when(opcionRepository.findByGrupoIdInOrderByOrdenAsc(anyCollection())).thenReturn(List.of(delgada, gruesa));

        // Se renombra el grupo, "Delgada" sube $500 y se quita "Gruesa"
        List<GrupoOpcionesDTO> nuevos = List.of(new GrupoOpcionesDTO(masaId, "Tipo de masa", 1, 1,
                List.of(new OpcionDTO(delgadaId, "Delgada", new BigDecimal("500"), true))));
        ProductoRequestDTO request = new ProductoRequestDTO(TIENDA_ID, "Pizza", "d", new BigDecimal("5000"), 10, true,
                "PIZZA", ingredientesValidos(), null, nuevos);
        controller.editar(PRODUCTO_ID, request, auth);

        assertEquals("Tipo de masa", masa.getNombre());
        assertEquals(0, delgada.getPrecioAdicional().compareTo(new BigDecimal("500")));
        verify(opcionRepository).deleteAll(argThat(l -> {
            List<OpcionProducto> lista = new ArrayList<>();
            l.forEach(lista::add);
            return lista.size() == 1 && lista.get(0) == gruesa;
        }));
        verify(grupoRepository, never()).deleteAll(any());
    }

    @Test
    void editar_conGruposVacios_borraTodosLosGruposYSusOpciones() {
        Producto existente = productoSimple();
        UUID masaId = UUID.randomUUID();
        GrupoOpciones masa = grupo(masaId, "Masa", 1, 1);
        OpcionProducto delgada = opcion(UUID.randomUUID(), masaId, "Delgada", "0");

        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(grupoRepository.findByProductoIdOrderByOrdenAsc(PRODUCTO_ID)).thenReturn(List.of(masa));
        when(opcionRepository.findByGrupoIdInOrderByOrdenAsc(anyCollection())).thenReturn(List.of(delgada));

        ProductoRequestDTO request = new ProductoRequestDTO(TIENDA_ID, "Pizza", "d", new BigDecimal("5000"), 10, true,
                "PIZZA", ingredientesValidos(), null, List.of());
        controller.editar(PRODUCTO_ID, request, auth);

        verify(opcionRepository).deleteAll(anyList());
        verify(grupoRepository).deleteAll(anyList());
        verify(opcionRepository, never()).saveAll(any());
    }

    @Test
    void editar_grupoInexistente_lanza400() {
        Producto existente = productoSimple();
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        List<GrupoOpcionesDTO> ajeno = List.of(new GrupoOpcionesDTO(UUID.randomUUID(), "Fantasma", 0, 1,
                List.of(op("A", "0"))));
        ProductoRequestDTO request = new ProductoRequestDTO(TIENDA_ID, "Pizza", "d", new BigDecimal("5000"), 10, true,
                "PIZZA", ingredientesValidos(), null, ajeno);

        assertThrows(ResponseStatusException.class, () -> controller.editar(PRODUCTO_ID, request, auth));
    }

    // ==================== reservarStock con variantes ====================

    @Test
    void reservarStock_productoConVariantesSinElegirOpcion_lanza400() {
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(productoSimple()));
        when(varianteRepository.existsByProductoId(PRODUCTO_ID)).thenReturn(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.reservarStock(PRODUCTO_ID, 1, null, INTERNAL_KEY));

        assertEquals(400, e.getStatusCode().value());
        verify(productoRepository, never()).reservarStock(any(), anyInt());
        verify(varianteRepository, never()).reservarStock(any(), any(), anyInt());
    }

    @Test
    void reservarStock_productoSinVariantesConVarianteId_lanza400() {
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(productoSimple()));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.reservarStock(PRODUCTO_ID, 1, UUID.randomUUID(), INTERNAL_KEY));

        assertEquals(400, e.getStatusCode().value());
    }

    @Test
    void reservarStock_conVariante_descuentaLaVarianteYAplicaLaOfertaSobreSuPrecio() {
        UUID familiarId = UUID.randomUUID();
        UUID ofertaId = UUID.randomUUID();
        Producto producto = productoSimple();
        VarianteProducto familiar = variante(familiarId, "Familiar", "12000", 3);
        ReservaStockResponseDTO reserva = new ReservaStockResponseDTO(ofertaId, new BigDecimal("8400"), 2);

        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(producto));
        when(varianteRepository.existsByProductoId(PRODUCTO_ID)).thenReturn(true);
        when(varianteRepository.findByIdAndProductoId(familiarId, PRODUCTO_ID)).thenReturn(Optional.of(familiar));
        when(varianteRepository.reservarStock(familiarId, PRODUCTO_ID, 2)).thenReturn(1);
        when(ofertaService.aplicarOferta(eq(producto), eq(new BigDecimal("12000")), eq(2), any(Instant.class)))
                .thenReturn(reserva);

        ResponseEntity<ReservaStockResponseDTO> response =
                controller.reservarStock(PRODUCTO_ID, 2, familiarId, INTERNAL_KEY);

        assertEquals(new BigDecimal("8400"), response.getBody().precioOferta());
        verify(productoRepository).descontarStockAgregado(PRODUCTO_ID, 2);
        verify(productoRepository, never()).reservarStock(any(), anyInt());
    }

    @Test
    void reservarStock_varianteSinStock_lanza409YNoTocaElTotal() {
        UUID familiarId = UUID.randomUUID();
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(productoSimple()));
        when(varianteRepository.existsByProductoId(PRODUCTO_ID)).thenReturn(true);
        when(varianteRepository.findByIdAndProductoId(familiarId, PRODUCTO_ID))
                .thenReturn(Optional.of(variante(familiarId, "Familiar", "12000", 0)));
        when(varianteRepository.reservarStock(familiarId, PRODUCTO_ID, 1)).thenReturn(0);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.reservarStock(PRODUCTO_ID, 1, familiarId, INTERNAL_KEY));

        assertEquals(409, e.getStatusCode().value());
        verify(productoRepository, never()).descontarStockAgregado(any(), anyInt());
    }

    @Test
    void reservarStock_varianteDeOtroProducto_lanza400() {
        UUID ajenaId = UUID.randomUUID();
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(productoSimple()));
        when(varianteRepository.existsByProductoId(PRODUCTO_ID)).thenReturn(true);
        when(varianteRepository.findByIdAndProductoId(ajenaId, PRODUCTO_ID)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.reservarStock(PRODUCTO_ID, 1, ajenaId, INTERNAL_KEY));

        assertEquals(400, e.getStatusCode().value());
    }

    // ==================== revertirStock con variantes ====================

    @Test
    void revertirStock_conVariante_devuelveALaVarianteYAlTotalYAlCupo() {
        UUID familiarId = UUID.randomUUID();
        UUID ofertaId = UUID.randomUUID();
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(productoSimple()));
        when(varianteRepository.revertirStock(familiarId, PRODUCTO_ID, 2)).thenReturn(1);

        controller.revertirStock(PRODUCTO_ID, 2, ofertaId, 1, familiarId, INTERNAL_KEY);

        verify(productoRepository).revertirStock(PRODUCTO_ID, 2);
        verify(ofertaService).devolverCupo(ofertaId, 1);
    }

    @Test
    void revertirStock_varianteYaBorrada_noSumaAlTotal() {
        UUID familiarId = UUID.randomUUID();
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(productoSimple()));
        when(varianteRepository.revertirStock(familiarId, PRODUCTO_ID, 2)).thenReturn(0);

        controller.revertirStock(PRODUCTO_ID, 2, null, 0, familiarId, INTERNAL_KEY);

        verify(productoRepository, never()).revertirStock(any(), anyInt());
    }
}