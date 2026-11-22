package com.nexo.msorders.controller;

import com.nexo.msorders.client.ProductoClient;
import com.nexo.msorders.client.PuntosClient;
import com.nexo.msorders.client.TiendaClient;
import com.nexo.msorders.dto.CrearPedidoRequestDTO;
import com.nexo.msorders.dto.PedidoResponseDTO;
import com.nexo.msorders.entity.ItemPedido;
import com.nexo.msorders.entity.Pedido;
import com.nexo.msorders.messaging.EventoPublisher;
import com.nexo.msorders.repository.ItemPedidoRepository;
import com.nexo.msorders.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoControllerCrearTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ItemPedidoRepository itemPedidoRepository;
    @Mock private TiendaClient tiendaClient;
    @Mock private ProductoClient productoClient;
    @Mock private EventoPublisher eventos;
    @Mock private PuntosClient puntosClient;
    @Mock private JwtAuthenticationToken auth;
    @Mock private Jwt jwt;

    @Captor private ArgumentCaptor<List<ItemPedido>> itemsCaptor;

    private PedidoController controller;

    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final UUID TIENDA_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new PedidoController(pedidoRepository, itemPedidoRepository,
                tiendaClient, productoClient, eventos, puntosClient);
        ReflectionTestUtils.setField(controller, "internalApiKey", "test-internal-key");
        ReflectionTestUtils.setField(controller, "pesosPorPuntoCanje", new BigDecimal("10"));
        ReflectionTestUtils.setField(controller, "topeCanjePorcentaje", new BigDecimal("10"));
        ReflectionTestUtils.setField(controller, "minimoCanje", 50L);
        lenient().when(auth.getPrincipal()).thenReturn(jwt);
    }

    private TiendaClient.TiendaDTO tiendaAbierta24h() {
        return new TiendaClient.TiendaDTO(TIENDA_ID, OWNER_ID, "APPROVED", null, "00:00-23:59");
    }

    private void clienteConTiendaAbierta() {
        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(tiendaClient.obtenerTienda(TIENDA_ID)).thenReturn(tiendaAbierta24h());
    }

    private void pedidoSeGuarda() {
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getEstado() == null) {
                p.setEstado(Pedido.Estado.CREATED);
            }
            return p;
        });
    }

    private ProductoClient.ProductoDTO productoDePrueba(UUID id, BigDecimal precio) {
        return new ProductoClient.ProductoDTO(id, TIENDA_ID, "Producto Test", precio, 10, true);
    }

    private ProductoClient.ProductoDTO pizzaConOpciones(UUID id, UUID personalId, UUID familiarId, UUID quesoId) {
        return new ProductoClient.ProductoDTO(id, TIENDA_ID, "Pizza Napolitana", new BigDecimal("6000"), 8, true,
                "PIZZA", false,
                List.of(new ProductoClient.VarianteDTO(personalId, "Personal", new BigDecimal("6000"), 5, true),
                        new ProductoClient.VarianteDTO(familiarId, "Familiar", new BigDecimal("12000"), 3, true)),
                List.of(new ProductoClient.GrupoDTO(UUID.randomUUID(), "Extras", 0, 3,
                        List.of(new ProductoClient.OpcionDTO(quesoId, "Queso extra", new BigDecimal("1000"), true)))));
    }

    private ProductoClient.ProductoDTO pizzaConMasaObligatoria(UUID id, UUID masaId, UUID delgadaId, UUID gruesaId,
                                                               boolean gruesaDisponible) {
        return new ProductoClient.ProductoDTO(id, TIENDA_ID, "Pizza Napolitana", new BigDecimal("6000"), 8, true,
                "PIZZA", false, List.of(),
                List.of(new ProductoClient.GrupoDTO(masaId, "Masa", 1, 1, List.of(
                        new ProductoClient.OpcionDTO(delgadaId, "Delgada", BigDecimal.ZERO, true),
                        new ProductoClient.OpcionDTO(gruesaId, "Gruesa", new BigDecimal("500"), gruesaDisponible)))));
    }

    private CrearPedidoRequestDTO pedidoDe(CrearPedidoRequestDTO.ItemRequest... items) {
        return new CrearPedidoRequestDTO(TIENDA_ID, "Calle Falsa 123", List.of(items));
    }

    // ==================== validaciones generales ====================

    @Test
    void crear_pedidoValido_seCreaConEstadoCorrecto() {
        clienteConTiendaAbierta();
        pedidoSeGuarda();
        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));

        ResponseEntity<PedidoResponseDTO> response =
                controller.crear(pedidoDe(new CrearPedidoRequestDTO.ItemRequest(productoId, 2)), auth);

        assertEquals(201, response.getStatusCode().value());
        verify(productoClient).reservarStock(productoId, null, 2);
        verify(puntosClient, never()).canjear(any(), anyLong());
        verify(pedidoRepository).save(argThat(p ->
                p.getSubtotal().compareTo(new BigDecimal("10000")) == 0
                        && p.getTotal().compareTo(new BigDecimal("12000")) == 0));
        verify(itemPedidoRepository).saveAll(itemsCaptor.capture());
        assertEquals("Producto Test", itemsCaptor.getValue().get(0).getNombreProducto());
    }

    @Test
    void crear_tiendaCerrada_lanza409() {
        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(tiendaClient.obtenerTienda(TIENDA_ID)).thenReturn(
                new TiendaClient.TiendaDTO(TIENDA_ID, OWNER_ID, "APPROVED", null, "08:00-08:01"));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> controller.crear(new CrearPedidoRequestDTO(TIENDA_ID, "Calle Falsa 123", List.of()), auth));

        assertEquals(409, excepcion.getStatusCode().value());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void crear_sinProductos_lanza400() {
        clienteConTiendaAbierta();

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> controller.crear(new CrearPedidoRequestDTO(TIENDA_ID, "Calle Falsa 123", List.of()), auth));

        assertEquals(400, excepcion.getStatusCode().value());
    }

    @Test
    void crear_noAlcanzaMontoMinimo_lanza400() {
        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(tiendaClient.obtenerTienda(TIENDA_ID)).thenReturn(
                new TiendaClient.TiendaDTO(TIENDA_ID, OWNER_ID, "APPROVED", new BigDecimal("50000"), "00:00-23:59"));
        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("1000")));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> controller.crear(pedidoDe(new CrearPedidoRequestDTO.ItemRequest(productoId, 1)), auth));

        assertEquals(400, excepcion.getStatusCode().value());
        verify(pedidoRepository, never()).save(any());
        verify(productoClient).revertirStock(productoId, null, 1, null, 0);
    }

    @Test
    void crear_productoDeOtraTienda_lanza400SinReservar() {
        clienteConTiendaAbierta();
        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(
                new ProductoClient.ProductoDTO(productoId, UUID.randomUUID(), "Ajeno", new BigDecimal("5000"), 10, true));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> controller.crear(pedidoDe(new CrearPedidoRequestDTO.ItemRequest(productoId, 1)), auth));

        assertEquals(400, excepcion.getStatusCode().value());
        verify(productoClient, never()).reservarStock(any(), any(), anyInt());
    }

    @Test
    void crear_productoNoDisponible_lanza409SinReservar() {
        clienteConTiendaAbierta();
        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(
                new ProductoClient.ProductoDTO(productoId, TIENDA_ID, "Agotado", new BigDecimal("5000"), 10, false));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> controller.crear(pedidoDe(new CrearPedidoRequestDTO.ItemRequest(productoId, 1)), auth));

        assertEquals(409, excepcion.getStatusCode().value());
        verify(productoClient, never()).reservarStock(any(), any(), anyInt());
    }

    @Test
    void crear_falloAlReservarElSegundoProducto_devuelveElStockDelPrimero() {
        clienteConTiendaAbierta();
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(productoClient.obtenerProducto(primero)).thenReturn(productoDePrueba(primero, new BigDecimal("5000")));
        when(productoClient.obtenerProducto(segundo)).thenReturn(productoDePrueba(segundo, new BigDecimal("3000")));
        doReturn(null).when(productoClient).reservarStock(primero, null, 2);
        when(productoClient.reservarStock(segundo, null, 1))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente"));

        assertThrows(ResponseStatusException.class, () -> controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(primero, 2),
                new CrearPedidoRequestDTO.ItemRequest(segundo, 1)), auth));

        verify(productoClient).revertirStock(primero, null, 2, null, 0);
        verify(pedidoRepository, never()).save(any());
    }

    // ==================== pago con puntos ====================

    @Test
    void crear_puntosNegativos_lanza400SinReservarStock() {
        clienteConTiendaAbierta();
        UUID productoId = UUID.randomUUID();

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> controller.crear(new CrearPedidoRequestDTO(TIENDA_ID, "Calle Falsa 123",
                        List.of(new CrearPedidoRequestDTO.ItemRequest(productoId, 1)), -5L), auth));

        assertEquals(400, excepcion.getStatusCode().value());
        verify(productoClient, never()).reservarStock(any(), any(), anyInt());
        verify(puntosClient, never()).canjear(any(), anyLong());
    }

    @Test
    void crear_puntosSuperanElTope_lanza400YDevuelveStock() {
        clienteConTiendaAbierta();
        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));

        // Subtotal 5000, tope 500 (50 puntos): 100 puntos = $1000 lo supera
        CrearPedidoRequestDTO request = new CrearPedidoRequestDTO(TIENDA_ID, "Calle Falsa 123",
                List.of(new CrearPedidoRequestDTO.ItemRequest(productoId, 1)), 100L);

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> controller.crear(request, auth));

        assertEquals(400, excepcion.getStatusCode().value());
        verify(productoClient).revertirStock(productoId, null, 1, null, 0);
        verify(puntosClient, never()).canjear(any(), anyLong());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void crear_puntosBajoElMinimo_lanza400YDevuelveStock() {
        clienteConTiendaAbierta();
        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("10000")));

        // 30 puntos ($300) están dentro del tope (10 % de 10000) pero bajo el mínimo de 50
        CrearPedidoRequestDTO request = new CrearPedidoRequestDTO(TIENDA_ID, "Calle Falsa 123",
                List.of(new CrearPedidoRequestDTO.ItemRequest(productoId, 1)), 30L);

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> controller.crear(request, auth));

        assertEquals(400, excepcion.getStatusCode().value());
        verify(productoClient).revertirStock(productoId, null, 1, null, 0);
        verify(puntosClient, never()).canjear(any(), anyLong());
    }

    @Test
    void crear_puntosDentroDelTope_canjeaYDescuenta() {
        clienteConTiendaAbierta();
        pedidoSeGuarda();
        UUID productoId = UUID.randomUUID();
        UUID canjeId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("10000")));
        when(puntosClient.canjear(CLIENTE_ID, 100L)).thenReturn(canjeId);

        // Subtotal 10000, tope 1000: 100 puntos = $1000, justo en el tope
        CrearPedidoRequestDTO request = new CrearPedidoRequestDTO(TIENDA_ID, "Calle Falsa 123",
                List.of(new CrearPedidoRequestDTO.ItemRequest(productoId, 1)), 100L);

        ResponseEntity<PedidoResponseDTO> response = controller.crear(request, auth);

        assertEquals(201, response.getStatusCode().value());
        verify(puntosClient).canjear(CLIENTE_ID, 100L);
        verify(pedidoRepository).save(argThat(p ->
                p.getTotal().compareTo(new BigDecimal("11000")) == 0
                        && p.getDescuentoPuntos().compareTo(new BigDecimal("1000")) == 0
                        && p.getPuntosCanjeados() == 100L
                        && canjeId.equals(p.getCanjeId())));
    }

    // ==================== ofertas ====================

    @Test
    void crear_ofertaParcial_separaEnDosLineasYCobraCadaUnaASuPrecio() {
        clienteConTiendaAbierta();
        pedidoSeGuarda();
        UUID productoId = UUID.randomUUID();
        UUID ofertaId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));
        // 30 % sobre $5.000: 2 de las 3 unidades salen a $3.500
        when(productoClient.reservarStock(productoId, null, 3))
                .thenReturn(new ProductoClient.ReservaDTO(ofertaId, new BigDecimal("3500"), 2));

        controller.crear(pedidoDe(new CrearPedidoRequestDTO.ItemRequest(productoId, 3)), auth);

        verify(itemPedidoRepository).saveAll(itemsCaptor.capture());
        List<ItemPedido> items = itemsCaptor.getValue();
        assertEquals(2, items.size());
        assertEquals(2, items.get(0).getCantidad());
        assertEquals(0, items.get(0).getPrecioUnitario().compareTo(new BigDecimal("3500")));
        assertEquals(1, items.get(1).getCantidad());
        assertEquals(0, items.get(1).getPrecioUnitario().compareTo(new BigDecimal("5000")));
        // 2 x 3.500 + 1 x 5.000 = 12.000; comisión 12 % = 1.440; total con envío = 14.000
        verify(pedidoRepository).save(argThat(p ->
                p.getSubtotal().compareTo(new BigDecimal("12000")) == 0
                        && p.getComisionPlataforma().compareTo(new BigDecimal("1440")) == 0
                        && p.getTotal().compareTo(new BigDecimal("14000")) == 0));
    }

    @Test
    void crear_ofertaPara_todasLasUnidades_generaUnaSolaLinea() {
        clienteConTiendaAbierta();
        pedidoSeGuarda();
        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));
        when(productoClient.reservarStock(productoId, null, 2))
                .thenReturn(new ProductoClient.ReservaDTO(UUID.randomUUID(), new BigDecimal("3500"), 2));

        controller.crear(pedidoDe(new CrearPedidoRequestDTO.ItemRequest(productoId, 2)), auth);

        verify(itemPedidoRepository).saveAll(itemsCaptor.capture());
        assertEquals(1, itemsCaptor.getValue().size());
        verify(pedidoRepository).save(argThat(p -> p.getSubtotal().compareTo(new BigDecimal("7000")) == 0));
    }

    @Test
    void crear_montoMinimoConOferta_devuelveStockYCupo() {
        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(tiendaClient.obtenerTienda(TIENDA_ID)).thenReturn(
                new TiendaClient.TiendaDTO(TIENDA_ID, OWNER_ID, "APPROVED", new BigDecimal("50000"), "00:00-23:59"));
        UUID productoId = UUID.randomUUID();
        UUID ofertaId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));
        when(productoClient.reservarStock(productoId, null, 2))
                .thenReturn(new ProductoClient.ReservaDTO(ofertaId, new BigDecimal("3500"), 2));

        assertThrows(ResponseStatusException.class,
                () -> controller.crear(pedidoDe(new CrearPedidoRequestDTO.ItemRequest(productoId, 2)), auth));

        verify(productoClient).revertirStock(productoId, null, 2, ofertaId, 2);
    }

    @Test
    void crear_puntosSobreElSubtotalConOferta_usanElPrecioRebajado() {
        clienteConTiendaAbierta();
        UUID productoId = UUID.randomUUID();
        UUID ofertaId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));
        when(productoClient.reservarStock(productoId, null, 2))
                .thenReturn(new ProductoClient.ReservaDTO(ofertaId, new BigDecimal("3500"), 2));

        // Subtotal real 7000, tope 700 (70 puntos): 100 puntos lo supera
        CrearPedidoRequestDTO request = new CrearPedidoRequestDTO(TIENDA_ID, "Calle Falsa 123",
                List.of(new CrearPedidoRequestDTO.ItemRequest(productoId, 2)), 100L);

        assertThrows(ResponseStatusException.class, () -> controller.crear(request, auth));

        verify(productoClient).revertirStock(productoId, null, 2, ofertaId, 2);
        verify(puntosClient, never()).canjear(any(), anyLong());
    }

    // ==================== variantes y opciones ====================

    @Test
    void crear_conTamanoYOpcionConRecargo_cobraLaVarianteMasElRecargo() {
        clienteConTiendaAbierta();
        pedidoSeGuarda();
        UUID pizzaId = UUID.randomUUID();
        UUID personalId = UUID.randomUUID();
        UUID familiarId = UUID.randomUUID();
        UUID quesoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(pizzaId)).thenReturn(pizzaConOpciones(pizzaId, personalId, familiarId, quesoId));

        controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(pizzaId, 2, familiarId, List.of(quesoId))), auth);

        verify(productoClient).reservarStock(pizzaId, familiarId, 2);
        verify(itemPedidoRepository).saveAll(itemsCaptor.capture());
        ItemPedido item = itemsCaptor.getValue().get(0);
        assertEquals(1, itemsCaptor.getValue().size());
        assertEquals("Familiar", item.getVarianteNombre());
        assertEquals(familiarId, item.getVarianteId());
        assertEquals(0, item.getPrecioUnitario().compareTo(new BigDecimal("13000")));
        assertEquals(0, item.getOpcionesUnitario().compareTo(new BigDecimal("1000")));
        assertTrue(item.getOpcionesDetalle().contains("Queso extra"));
        // 2 x (12.000 + 1.000) = 26.000
        verify(pedidoRepository).save(argThat(p -> p.getSubtotal().compareTo(new BigDecimal("26000")) == 0));
    }

    @Test
    void crear_ofertaSobreUnaVariante_lasOpcionesConRecargoSeCobranCompletas() {
        clienteConTiendaAbierta();
        pedidoSeGuarda();
        UUID pizzaId = UUID.randomUUID();
        UUID personalId = UUID.randomUUID();
        UUID familiarId = UUID.randomUUID();
        UUID quesoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(pizzaId)).thenReturn(pizzaConOpciones(pizzaId, personalId, familiarId, quesoId));
        // 30 % sobre la familiar ($12.000): 1 unidad a $8.400
        when(productoClient.reservarStock(pizzaId, familiarId, 1))
                .thenReturn(new ProductoClient.ReservaDTO(UUID.randomUUID(), new BigDecimal("8400"), 1));

        controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(pizzaId, 1, familiarId, List.of(quesoId))), auth);

        verify(itemPedidoRepository).saveAll(itemsCaptor.capture());
        assertEquals(0, itemsCaptor.getValue().get(0).getPrecioUnitario().compareTo(new BigDecimal("9400")));
    }

    @Test
    void crear_productoConTamanosSinElegirOpcion_lanza400SinReservar() {
        clienteConTiendaAbierta();
        UUID pizzaId = UUID.randomUUID();
        when(productoClient.obtenerProducto(pizzaId)).thenReturn(
                pizzaConOpciones(pizzaId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> controller.crear(pedidoDe(new CrearPedidoRequestDTO.ItemRequest(pizzaId, 1)), auth));

        assertEquals(400, excepcion.getStatusCode().value());
        verify(productoClient, never()).reservarStock(any(), any(), anyInt());
    }

    @Test
    void crear_opcionQueNoExiste_lanza400() {
        clienteConTiendaAbierta();
        UUID pizzaId = UUID.randomUUID();
        when(productoClient.obtenerProducto(pizzaId)).thenReturn(
                pizzaConOpciones(pizzaId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertThrows(ResponseStatusException.class, () -> controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(pizzaId, 1, UUID.randomUUID(), List.of())), auth));
        verify(productoClient, never()).reservarStock(any(), any(), anyInt());
    }

    @Test
    void crear_opcionElegidaQueNoExiste_lanza400() {
        clienteConTiendaAbierta();
        UUID pizzaId = UUID.randomUUID();
        UUID personalId = UUID.randomUUID();
        when(productoClient.obtenerProducto(pizzaId)).thenReturn(
                pizzaConOpciones(pizzaId, personalId, UUID.randomUUID(), UUID.randomUUID()));

        assertThrows(ResponseStatusException.class, () -> controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(pizzaId, 1, personalId, List.of(UUID.randomUUID()))), auth));
        verify(productoClient, never()).reservarStock(any(), any(), anyInt());
    }

    @Test
    void crear_varianteIdEnProductoSinOpciones_lanza400() {
        clienteConTiendaAbierta();
        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));

        assertThrows(ResponseStatusException.class, () -> controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(productoId, 1, UUID.randomUUID(), List.of())), auth));
    }

    @Test
    void crear_falloAlReservarConVariante_devuelveLasReservasPrevias() {
        clienteConTiendaAbierta();
        UUID productoId = UUID.randomUUID();
        UUID pizzaId = UUID.randomUUID();
        UUID personalId = UUID.randomUUID();
        UUID familiarId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));
        when(productoClient.obtenerProducto(pizzaId)).thenReturn(
                pizzaConOpciones(pizzaId, personalId, familiarId, UUID.randomUUID()));
        doReturn(null).when(productoClient).reservarStock(productoId, null, 1);
        when(productoClient.reservarStock(pizzaId, personalId, 1))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente"));

        assertThrows(ResponseStatusException.class, () -> controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(productoId, 1),
                new CrearPedidoRequestDTO.ItemRequest(pizzaId, 1, personalId, List.of())), auth));

        verify(productoClient).revertirStock(productoId, null, 1, null, 0);
    }

    // ==================== grupos de opciones y nota ====================

    @Test
    void crear_grupoObligatorioSinElegir_lanza400SinReservar() {
        clienteConTiendaAbierta();
        UUID pizzaId = UUID.randomUUID();
        when(productoClient.obtenerProducto(pizzaId)).thenReturn(pizzaConMasaObligatoria(
                pizzaId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> controller.crear(pedidoDe(new CrearPedidoRequestDTO.ItemRequest(pizzaId, 1)), auth));

        assertEquals(400, excepcion.getStatusCode().value());
        assertTrue(excepcion.getReason().contains("Masa"));
        verify(productoClient, never()).reservarStock(any(), any(), anyInt());
    }

    @Test
    void crear_grupoObligatorioElegido_sumaElRecargoYGuardaElDetalle() {
        clienteConTiendaAbierta();
        pedidoSeGuarda();
        UUID pizzaId = UUID.randomUUID();
        UUID masaId = UUID.randomUUID();
        UUID delgadaId = UUID.randomUUID();
        UUID gruesaId = UUID.randomUUID();
        when(productoClient.obtenerProducto(pizzaId)).thenReturn(
                pizzaConMasaObligatoria(pizzaId, masaId, delgadaId, gruesaId, true));

        controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(pizzaId, 1, null, List.of(gruesaId))), auth);

        verify(itemPedidoRepository).saveAll(itemsCaptor.capture());
        ItemPedido item = itemsCaptor.getValue().get(0);
        assertEquals(0, item.getPrecioUnitario().compareTo(new BigDecimal("6500")));
        assertEquals(0, item.getOpcionesUnitario().compareTo(new BigDecimal("500")));
        assertTrue(item.getOpcionesDetalle().contains("Masa"));
        assertTrue(item.getOpcionesDetalle().contains("Gruesa"));
    }

    @Test
    void crear_masOpcionesQueElMaximoDelGrupo_lanza400SinReservar() {
        clienteConTiendaAbierta();
        UUID pizzaId = UUID.randomUUID();
        UUID delgadaId = UUID.randomUUID();
        UUID gruesaId = UUID.randomUUID();
        when(productoClient.obtenerProducto(pizzaId)).thenReturn(
                pizzaConMasaObligatoria(pizzaId, UUID.randomUUID(), delgadaId, gruesaId, true));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class, () -> controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(pizzaId, 1, null, List.of(delgadaId, gruesaId))), auth));

        assertEquals(400, excepcion.getStatusCode().value());
        verify(productoClient, never()).reservarStock(any(), any(), anyInt());
    }

    @Test
    void crear_opcionNoDisponible_lanza409SinReservar() {
        clienteConTiendaAbierta();
        UUID pizzaId = UUID.randomUUID();
        UUID gruesaId = UUID.randomUUID();
        when(productoClient.obtenerProducto(pizzaId)).thenReturn(
                pizzaConMasaObligatoria(pizzaId, UUID.randomUUID(), UUID.randomUUID(), gruesaId, false));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class, () -> controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(pizzaId, 1, null, List.of(gruesaId))), auth));

        assertEquals(409, excepcion.getStatusCode().value());
        verify(productoClient, never()).reservarStock(any(), any(), anyInt());
    }

    @Test
    void crear_notaSeGuardaSinEspaciosSobrantes() {
        clienteConTiendaAbierta();
        pedidoSeGuarda();
        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));

        controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(productoId, 1, null, List.of(), "  sin cebolla  ")), auth);

        verify(itemPedidoRepository).saveAll(itemsCaptor.capture());
        assertEquals("sin cebolla", itemsCaptor.getValue().get(0).getNota());
    }

    @Test
    void crear_notaEnBlanco_noSeGuarda() {
        clienteConTiendaAbierta();
        pedidoSeGuarda();
        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));

        controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(productoId, 1, null, List.of(), "   ")), auth);

        verify(itemPedidoRepository).saveAll(itemsCaptor.capture());
        assertNull(itemsCaptor.getValue().get(0).getNota());
    }

    @Test
    void crear_notaMuyLarga_lanza400SinReservar() {
        clienteConTiendaAbierta();
        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class, () -> controller.crear(pedidoDe(
                new CrearPedidoRequestDTO.ItemRequest(productoId, 1, null, List.of(), "x".repeat(141))), auth));

        assertEquals(400, excepcion.getStatusCode().value());
        verify(productoClient, never()).reservarStock(any(), any(), anyInt());
    }

    // ==================== alcohol ====================

    private ProductoClient.ProductoDTO cervezaConAlcohol(UUID id) {
        return new ProductoClient.ProductoDTO(id, TIENDA_ID, "Cerveza artesanal", new BigDecimal("3990"), 10, true,
                "BEBIDA", true, List.of(), List.of());
    }

    @Test
    void crear_alcoholSinConfirmarMayorEdad_lanza400SinReservar() {
        clienteConTiendaAbierta();
        UUID cervezaId = UUID.randomUUID();
        when(productoClient.obtenerProducto(cervezaId)).thenReturn(cervezaConAlcohol(cervezaId));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> controller.crear(pedidoDe(new CrearPedidoRequestDTO.ItemRequest(cervezaId, 1)), auth));

        assertEquals(400, excepcion.getStatusCode().value());
        verify(productoClient, never()).reservarStock(any(), any(), anyInt());
    }

    @Test
    void crear_alcoholConMayorEdadConfirmada_creaElPedido() {
        clienteConTiendaAbierta();
        pedidoSeGuarda();
        UUID cervezaId = UUID.randomUUID();
        when(productoClient.obtenerProducto(cervezaId)).thenReturn(cervezaConAlcohol(cervezaId));

        CrearPedidoRequestDTO request = new CrearPedidoRequestDTO(TIENDA_ID, "Calle Falsa 123",
                List.of(new CrearPedidoRequestDTO.ItemRequest(cervezaId, 1)), 0L, true);

        ResponseEntity<PedidoResponseDTO> response = controller.crear(request, auth);

        assertEquals(201, response.getStatusCode().value());
        verify(productoClient).reservarStock(cervezaId, null, 1);
    }
}