package com.nexo.msorders.controller;

import com.nexo.msorders.client.ConductorClient;
import com.nexo.msorders.client.NotificacionClient;
import com.nexo.msorders.client.ProductoClient;
import com.nexo.msorders.client.TiendaClient;
import com.nexo.msorders.dto.CancelarPedidoRequestDTO;
import com.nexo.msorders.dto.CrearPedidoRequestDTO;
import com.nexo.msorders.dto.PedidoResponseDTO;
import com.nexo.msorders.entity.PagoSimulado;
import com.nexo.msorders.entity.Pedido;
import com.nexo.msorders.repository.ItemPedidoRepository;
import com.nexo.msorders.repository.PedidoRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoControllerTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ItemPedidoRepository itemPedidoRepository;
    @Mock private TiendaClient tiendaClient;
    @Mock private ProductoClient productoClient;
    @Mock private NotificacionClient notificacionClient;
    @Mock private ConductorClient conductorClient;
    @Mock private JwtAuthenticationToken auth;
    @Mock private Jwt jwt;

    private PedidoController controller;

    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final UUID TIENDA_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final String INTERNAL_KEY = "test-internal-key";

    @BeforeEach
    void setUp() {
        controller = new PedidoController(pedidoRepository, itemPedidoRepository,
                tiendaClient, productoClient, notificacionClient, conductorClient);
        ReflectionTestUtils.setField(controller, "internalApiKey", INTERNAL_KEY);
        lenient().when(auth.getPrincipal()).thenReturn(jwt);
    }

    private Pedido crearPedidoDePrueba(Pedido.Estado estado) {
        Pedido pedido = new Pedido();
        pedido.setClienteId(CLIENTE_ID);
        pedido.setTiendaId(TIENDA_ID);
        pedido.setEstado(estado);
        pedido.setSubtotal(new BigDecimal("10000"));
        pedido.setCostoEnvio(new BigDecimal("2000"));
        pedido.setTotal(new BigDecimal("12000"));
        PagoSimulado pago = new PagoSimulado();
        pago.setEstado(PagoSimulado.Estado.PENDING);
        pedido.setPago(pago);
        return pedido;
    }

    private TiendaClient.TiendaDTO tiendaAbierta24h() {
        return new TiendaClient.TiendaDTO(TIENDA_ID, OWNER_ID, "APPROVED", null, "00:00-23:59");
    }

    private ProductoClient.ProductoDTO productoDePrueba(UUID id, BigDecimal precio) {
        return new ProductoClient.ProductoDTO(id, TIENDA_ID, "Producto Test", precio, 10, true);
    }

    // ==================== crear ====================

    @Test
    void crear_pedidoValido_seCreaConEstadoCorrecto() {
        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(tiendaClient.obtenerTienda(TIENDA_ID)).thenReturn(tiendaAbierta24h());

        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("5000")));

        CrearPedidoRequestDTO request = new CrearPedidoRequestDTO(
                TIENDA_ID, "Calle Falsa 123",
                List.of(new CrearPedidoRequestDTO.ItemRequest(productoId, 2))
        );

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getEstado() == null) p.setEstado(Pedido.Estado.CREATED);
            return p;
        });

        ResponseEntity<PedidoResponseDTO> response = controller.crear(request, auth);

        assertEquals(201, response.getStatusCode().value());
        verify(productoClient).reservarStock(productoId, 2);
        verify(pedidoRepository).save(argThat(p ->
                p.getSubtotal().compareTo(new BigDecimal("10000")) == 0
                        && p.getTotal().compareTo(new BigDecimal("12000")) == 0
        ));
    }

    @Test
    void crear_tiendaCerrada_lanza409() {
        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        TiendaClient.TiendaDTO tiendaCerrada =
                new TiendaClient.TiendaDTO(TIENDA_ID, OWNER_ID, "APPROVED", null, "08:00-08:01");
        when(tiendaClient.obtenerTienda(TIENDA_ID)).thenReturn(tiendaCerrada);

        CrearPedidoRequestDTO request = new CrearPedidoRequestDTO(
                TIENDA_ID, "Calle Falsa 123", List.of()
        );

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.crear(request, auth)
        );
        assertEquals(409, excepcion.getStatusCode().value());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void crear_noAlcanzaMontoMinimo_lanza400() {
        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        TiendaClient.TiendaDTO tiendaConMinimo =
                new TiendaClient.TiendaDTO(TIENDA_ID, OWNER_ID, "APPROVED", new BigDecimal("50000"), "00:00-23:59");
        when(tiendaClient.obtenerTienda(TIENDA_ID)).thenReturn(tiendaConMinimo);

        UUID productoId = UUID.randomUUID();
        when(productoClient.obtenerProducto(productoId)).thenReturn(productoDePrueba(productoId, new BigDecimal("1000")));

        CrearPedidoRequestDTO request = new CrearPedidoRequestDTO(
                TIENDA_ID, "Calle Falsa 123",
                List.of(new CrearPedidoRequestDTO.ItemRequest(productoId, 1))
        );

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.crear(request, auth)
        );
        assertEquals(400, excepcion.getStatusCode().value());
        verify(pedidoRepository, never()).save(any());
    }

    // ==================== pagar ====================

    @Test
    void pagar_pedidoCreated_pasaAPaid() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.CREATED);

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tiendaClient.obtenerTienda(TIENDA_ID)).thenReturn(tiendaAbierta24h());

        PedidoResponseDTO resultado = controller.pagar(id, auth);

        assertEquals(Pedido.Estado.PAID, pedido.getEstado());
        assertEquals(PagoSimulado.Estado.APPROVED, pedido.getPago().getEstado());
        verify(notificacionClient, atLeastOnce()).enviar(any(), anyString(), anyString());
    }

    @Test
    void pagar_pedidoNoEstaEnCreated_lanza409() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.PAID);

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        assertThrows(ResponseStatusException.class, () -> controller.pagar(id, auth));
    }

    // ==================== cancelar ====================

    @Test
    void cancelar_enCreated_sinCargoReembolsoTotal() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.CREATED);
        CancelarPedidoRequestDTO request = new CancelarPedidoRequestDTO("Cambié de opinión");

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(itemPedidoRepository.findByPedidoId(any())).thenReturn(List.of());
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.cancelar(id, request, auth);

        assertEquals(Pedido.Estado.CANCELLED, pedido.getEstado());
        assertEquals(0, pedido.getPago().getMontoReembolsado().compareTo(pedido.getTotal()));
        assertNull(pedido.getCargoCancelacion());
    }

    @Test
    void cancelar_enReady_conCargoDelVeinte() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.READY);
        CancelarPedidoRequestDTO request = new CancelarPedidoRequestDTO("Ya no lo necesito");

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(itemPedidoRepository.findByPedidoId(any())).thenReturn(List.of());
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.cancelar(id, request, auth);

        BigDecimal cargoEsperado = new BigDecimal("10000").multiply(new BigDecimal("0.20"));
        assertEquals(0, pedido.getCargoCancelacion().compareTo(cargoEsperado));
    }

    @Test
    void cancelar_estadoNoAdmiteCancelacion_lanza409() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.DELIVERED);
        CancelarPedidoRequestDTO request = new CancelarPedidoRequestDTO("Motivo");

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        assertThrows(ResponseStatusException.class, () -> controller.cancelar(id, request, auth));
        verify(pedidoRepository, never()).save(any());
    }

    // ==================== tomarOrden / marcarListo ====================

    @Test
    void tomarOrden_desdeEstadoPaid_pasaAConfirmed() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.PAID);

        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.tomarOrden(id);

        assertEquals(Pedido.Estado.CONFIRMED, pedido.getEstado());
    }

    @Test
    void tomarOrden_transicionInvalida_lanza409() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.CREATED);

        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        assertThrows(ResponseStatusException.class, () -> controller.tomarOrden(id));
    }

    @Test
    void marcarListo_notificaConductoresDisponibles() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.CONFIRMED);
        UUID conductorSub = UUID.randomUUID();

        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conductorClient.obtenerConductoresDisponibles()).thenReturn(List.of(conductorSub.toString()));

        controller.marcarListo(id);

        assertEquals(Pedido.Estado.READY, pedido.getEstado());
        verify(notificacionClient).enviar(eq(conductorSub), anyString(), anyString());
    }

    // ==================== aceptar ====================

    @Test
    void aceptar_pedidoLibre_seAsignaCorrectamente() {
        UUID id = UUID.randomUUID();
        UUID conductorId = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.READY);

        when(jwt.getClaimAsString("sub")).thenReturn(conductorId.toString());
        when(pedidoRepository.asignarConductor(id, conductorId)).thenReturn(1);
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(tiendaClient.obtenerTienda(TIENDA_ID)).thenReturn(tiendaAbierta24h());

        PedidoResponseDTO resultado = controller.aceptar(id, auth);

        assertNotNull(resultado);
        verify(pedidoRepository).asignarConductor(id, conductorId);
    }

    @Test
    void aceptar_pedidoYaTomado_lanza409() {
        UUID id = UUID.randomUUID();
        UUID conductorId = UUID.randomUUID();

        when(jwt.getClaimAsString("sub")).thenReturn(conductorId.toString());
        when(pedidoRepository.asignarConductor(id, conductorId)).thenReturn(0);

        assertThrows(ResponseStatusException.class, () -> controller.aceptar(id, auth));
        verify(pedidoRepository, never()).findById(any());
    }

    // ==================== entregar ====================

    @Test
    void entregar_conductorAsignadoYEnDelivering_marcaEntregado() {
        UUID id = UUID.randomUUID();
        UUID conductorId = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.DELIVERING);
        pedido.setConductorId(conductorId);

        when(jwt.getClaimAsString("sub")).thenReturn(conductorId.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.entregar(id, auth);

        assertEquals(Pedido.Estado.DELIVERED, pedido.getEstado());
    }

    @Test
    void entregar_conductorDistinto_lanza403() {
        UUID id = UUID.randomUUID();
        UUID conductorReal = UUID.randomUUID();
        UUID otroConductor = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.DELIVERING);
        pedido.setConductorId(conductorReal);

        when(jwt.getClaimAsString("sub")).thenReturn(otroConductor.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        assertThrows(ResponseStatusException.class, () -> controller.entregar(id, auth));
    }

    @Test
    void entregar_pedidoNoEstaEnDelivering_lanza403() {
        UUID id = UUID.randomUUID();
        UUID conductorId = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.READY);
        pedido.setConductorId(conductorId);

        when(jwt.getClaimAsString("sub")).thenReturn(conductorId.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        assertThrows(ResponseStatusException.class, () -> controller.entregar(id, auth));
    }

    // ==================== confirmarRecepcion ====================

    @Test
    void confirmarRecepcion_pedidoDelivered_pasaACompleted() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.DELIVERED);

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.confirmarRecepcion(id, auth);

        assertEquals(Pedido.Estado.COMPLETED, pedido.getEstado());
    }

    @Test
    void confirmarRecepcion_pedidoNoEntregado_lanza409() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.READY);

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        assertThrows(ResponseStatusException.class, () -> controller.confirmarRecepcion(id, auth));
    }

    // ==================== detalle / buscarPropio ====================

    @Test
    void detalle_dueñoReal_loDevuelve() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.CREATED);

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        assertNotNull(controller.detalle(id, auth));
    }

    @Test
    void detalle_otroUsuario_lanza403() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.CREATED);
        UUID otroUsuario = UUID.randomUUID();

        when(jwt.getClaimAsString("sub")).thenReturn(otroUsuario.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> controller.detalle(id, auth)
        );
        assertEquals(403, excepcion.getStatusCode().value());
    }

    // ==================== verificarCompletado (endpoint interno) ====================

    @Test
    void verificarCompletado_sinKeyValida_lanza403() {
        UUID id = UUID.randomUUID();

        assertThrows(
                ResponseStatusException.class,
                () -> controller.verificarCompletado(id, CLIENTE_ID, null, "key-incorrecta")
        );
    }

    @Test
    void verificarCompletado_pedidoCompletadoDelCliente_devuelveTrue() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.COMPLETED);

        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        Map<String, Boolean> resultado = controller.verificarCompletado(id, CLIENTE_ID, null, INTERNAL_KEY);

        assertTrue(resultado.get("valido"));
    }

    @Test
    void verificarCompletado_pedidoDeOtroCliente_devuelveFalse() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.COMPLETED);
        UUID otroCliente = UUID.randomUUID();

        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        Map<String, Boolean> resultado = controller.verificarCompletado(id, otroCliente, null, INTERNAL_KEY);

        assertFalse(resultado.get("valido"));
    }
}