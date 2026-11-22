package com.nexo.msorders.controller;

import com.nexo.msorders.client.ProductoClient;
import com.nexo.msorders.client.PuntosClient;
import com.nexo.msorders.client.TiendaClient;
import com.nexo.msorders.dto.CancelarPedidoRequestDTO;
import com.nexo.msorders.dto.PedidoResponseDTO;
import com.nexo.msorders.entity.PagoSimulado;
import com.nexo.msorders.entity.Pedido;
import com.nexo.msorders.messaging.EventoPublisher;
import com.nexo.msorders.repository.ItemPedidoRepository;
import com.nexo.msorders.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoControllerTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ItemPedidoRepository itemPedidoRepository;
    @Mock private TiendaClient tiendaClient;
    @Mock private ProductoClient productoClient;
    @Mock private EventoPublisher eventos;
    @Mock private PuntosClient puntosClient;
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
                tiendaClient, productoClient, eventos, puntosClient);
        ReflectionTestUtils.setField(controller, "internalApiKey", INTERNAL_KEY);
        ReflectionTestUtils.setField(controller, "pesosPorPuntoCanje", new BigDecimal("10"));
        ReflectionTestUtils.setField(controller, "topeCanjePorcentaje", new BigDecimal("10"));
        ReflectionTestUtils.setField(controller, "minimoCanje", 50L);
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

    // ==================== pagar ====================

    @Test
    void pagar_pedidoCreated_pasaAPaidYPublicaEvento() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.CREATED);

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tiendaClient.obtenerTienda(TIENDA_ID)).thenReturn(tiendaAbierta24h());

        controller.pagar(id, auth);

        assertEquals(Pedido.Estado.PAID, pedido.getEstado());
        assertEquals(PagoSimulado.Estado.APPROVED, pedido.getPago().getEstado());
        verify(eventos).publicar(argThat(e ->
                "pedido.pagado".equals(e.evento()) && OWNER_ID.equals(e.ownerId())));
    }

    @Test
    void pagar_tiendaNoResponde_publicaEventoSinOwner() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.CREATED);

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tiendaClient.obtenerTienda(TIENDA_ID)).thenThrow(new RuntimeException("ms-stores caído"));

        controller.pagar(id, auth);

        assertEquals(Pedido.Estado.PAID, pedido.getEstado());
        verify(eventos).publicar(argThat(e ->
                "pedido.pagado".equals(e.evento()) && e.ownerId() == null));
    }

    @Test
    void pagar_pedidoNoEstaEnCreated_lanza409() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.PAID);

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        assertThrows(ResponseStatusException.class, () -> controller.pagar(id, auth));
        verify(eventos, never()).publicar(any());
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
        verify(eventos).publicar(argThat(e -> "pedido.cancelado".equals(e.evento())));
        verify(productoClient, never()).revertirStock(any(), any(), anyInt(), any(), anyInt());
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
        verify(eventos, never()).publicar(any());
    }

    // ==================== tomarOrden / marcarListo ====================

    @Test
    void tomarOrden_desdeEstadoPaid_pasaAConfirmedYPublicaEvento() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.PAID);

        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.tomarOrden(id);

        assertEquals(Pedido.Estado.CONFIRMED, pedido.getEstado());
        verify(eventos).publicar(argThat(e -> "pedido.confirmado".equals(e.evento())));
    }

    @Test
    void tomarOrden_transicionInvalida_lanza409() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.CREATED);

        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        assertThrows(ResponseStatusException.class, () -> controller.tomarOrden(id));
        verify(eventos, never()).publicar(any());
    }

    @Test
    void marcarListo_pasaAReadyYPublicaEvento() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.CONFIRMED);

        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.marcarListo(id);

        assertEquals(Pedido.Estado.READY, pedido.getEstado());
        verify(eventos).publicar(argThat(e -> "pedido.listo".equals(e.evento())));
    }

    // ==================== aceptar ====================

    @Test
    void aceptar_pedidoLibre_seAsignaYPublicaEvento() {
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
        verify(eventos).publicar(argThat(e ->
                "pedido.aceptado".equals(e.evento()) && OWNER_ID.equals(e.ownerId())));
    }

    @Test
    void aceptar_pedidoYaTomado_lanza409() {
        UUID id = UUID.randomUUID();
        UUID conductorId = UUID.randomUUID();

        when(jwt.getClaimAsString("sub")).thenReturn(conductorId.toString());
        when(pedidoRepository.asignarConductor(id, conductorId)).thenReturn(0);

        assertThrows(ResponseStatusException.class, () -> controller.aceptar(id, auth));
        verify(pedidoRepository, never()).findById(any());
        verify(eventos, never()).publicar(any());
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
        verify(eventos).publicar(argThat(e -> "pedido.entregado".equals(e.evento())));
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
    void confirmarRecepcion_pedidoDelivered_pasaACompletedYPublicaEvento() {
        UUID id = UUID.randomUUID();
        Pedido pedido = crearPedidoDePrueba(Pedido.Estado.DELIVERED);

        when(jwt.getClaimAsString("sub")).thenReturn(CLIENTE_ID.toString());
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.confirmarRecepcion(id, auth);

        assertEquals(Pedido.Estado.COMPLETED, pedido.getEstado());
        verify(eventos).publicar(argThat(e -> "pedido.completado".equals(e.evento())));
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