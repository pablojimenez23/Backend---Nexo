package com.nexo.msproducts.service;

import com.nexo.msproducts.dto.OfertaRequestDTO;
import com.nexo.msproducts.dto.ReservaStockResponseDTO;
import com.nexo.msproducts.entity.Oferta;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.repository.OfertaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfertaServiceTest {

    @Mock private OfertaRepository ofertas;

    private OfertaService service;

    private static final UUID PRODUCTO_ID = UUID.randomUUID();
    private static final UUID TIENDA_ID = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-10-23T15:00:00Z");

    @BeforeEach
    void setUp() {
        service = new OfertaService(ofertas);
    }

    private Producto producto(String precio, int stock) {
        Producto p = new Producto();
        ReflectionTestUtils.setField(p, "id", PRODUCTO_ID);
        p.setTiendaId(TIENDA_ID);
        p.setNombre("Producto Test");
        p.setPrecio(new BigDecimal(precio));
        p.setStock(stock);
        return p;
    }

    private OfertaRequestDTO req(Integer porcentaje, Integer horas) {
        return new OfertaRequestDTO(PRODUCTO_ID, porcentaje, horas, null, null, null);
    }

    private Oferta ofertaVigente(int porcentaje, int limite, Integer cupo, int vendidas) {
        Oferta o = new Oferta();
        ReflectionTestUtils.setField(o, "id", UUID.randomUUID());
        o.setProductoId(PRODUCTO_ID);
        o.setTiendaId(TIENDA_ID);
        o.setPorcentaje(porcentaje);
        o.setInicio(AHORA.minus(1, ChronoUnit.HOURS));
        o.setFin(AHORA.plus(5, ChronoUnit.HOURS));
        o.setLimitePorPedido(limite);
        o.setCupo(cupo);
        o.setUnidadesVendidas(vendidas);
        return o;
    }

    private void assertRechaza(int status, org.junit.jupiter.api.function.Executable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion);
        assertEquals(status, e.getStatusCode().value());
        verify(ofertas, never()).save(any());
    }

    // ==================== crear: validaciones ====================

    @Test
    void crear_valida_guardaConFinCorrecto() {
        when(ofertas.save(any(Oferta.class))).thenAnswer(inv -> inv.getArgument(0));

        Oferta oferta = service.crear(producto("5000", 50), req(30, 24), AHORA);

        assertEquals(30, oferta.getPorcentaje());
        assertEquals(AHORA, oferta.getInicio());
        assertEquals(AHORA.plus(24, ChronoUnit.HOURS), oferta.getFin());
        assertEquals(2, oferta.getLimitePorPedido());
        assertNull(oferta.getCupo());
        assertEquals(Oferta.Estado.ACTIVA, oferta.getEstado());
        assertEquals(PRODUCTO_ID, oferta.getProductoId());
        assertEquals(TIENDA_ID, oferta.getTiendaId());
    }

    @Test
    void crear_descuentoBajoElMinimo_lanza400() {
        assertRechaza(400, () -> service.crear(producto("5000", 50), req(5, 24), AHORA));
    }

    @Test
    void crear_descuentoSobreElMaximo_lanza400() {
        assertRechaza(400, () -> service.crear(producto("5000", 50), req(50, 24), AHORA));
    }

    @Test
    void crear_sinDescuento_lanza400() {
        assertRechaza(400, () -> service.crear(producto("5000", 50), req(null, 24), AHORA));
    }

    @Test
    void crear_duracionCorta_lanza400() {
        assertRechaza(400, () -> service.crear(producto("5000", 50), req(30, 2), AHORA));
    }

    @Test
    void crear_duracionLarga_lanza400() {
        assertRechaza(400, () -> service.crear(producto("5000", 50), req(30, 100), AHORA));
    }

    @Test
    void crear_productoBarato_lanza400() {
        assertRechaza(400, () -> service.crear(producto("900", 50), req(30, 24), AHORA));
    }

    @Test
    void crear_inicioEnElPasado_lanza400() {
        OfertaRequestDTO r = new OfertaRequestDTO(PRODUCTO_ID, 30, 24, AHORA.minus(1, ChronoUnit.HOURS), null, null);
        assertRechaza(400, () -> service.crear(producto("5000", 50), r, AHORA));
    }

    @Test
    void crear_inicioMuyLejano_lanza400() {
        OfertaRequestDTO r = new OfertaRequestDTO(PRODUCTO_ID, 30, 24, AHORA.plus(10, ChronoUnit.DAYS), null, null);
        assertRechaza(400, () -> service.crear(producto("5000", 50), r, AHORA));
    }

    @Test
    void crear_inicioProgramado_usaEsaFecha() {
        when(ofertas.save(any(Oferta.class))).thenAnswer(inv -> inv.getArgument(0));
        Instant inicio = AHORA.plus(2, ChronoUnit.DAYS);
        OfertaRequestDTO r = new OfertaRequestDTO(PRODUCTO_ID, 20, 12, inicio, null, null);

        Oferta oferta = service.crear(producto("5000", 50), r, AHORA);

        assertEquals(inicio, oferta.getInicio());
        assertEquals(inicio.plus(12, ChronoUnit.HOURS), oferta.getFin());
    }

    @Test
    void crear_cupoMayorAlStock_lanza400() {
        OfertaRequestDTO r = new OfertaRequestDTO(PRODUCTO_ID, 30, 24, null, 30, null);
        assertRechaza(400, () -> service.crear(producto("5000", 10), r, AHORA));
    }

    @Test
    void crear_cupoBajoElMinimo_lanza400() {
        OfertaRequestDTO r = new OfertaRequestDTO(PRODUCTO_ID, 30, 24, null, 2, null);
        assertRechaza(400, () -> service.crear(producto("5000", 50), r, AHORA));
    }

    @Test
    void crear_cupoValido_loGuarda() {
        when(ofertas.save(any(Oferta.class))).thenAnswer(inv -> inv.getArgument(0));
        OfertaRequestDTO r = new OfertaRequestDTO(PRODUCTO_ID, 30, 24, null, 20, 3);

        Oferta oferta = service.crear(producto("5000", 50), r, AHORA);

        assertEquals(20, oferta.getCupo());
        assertEquals(3, oferta.getLimitePorPedido());
    }

    @Test
    void crear_limiteSobreElMaximo_lanza400() {
        OfertaRequestDTO r = new OfertaRequestDTO(PRODUCTO_ID, 30, 24, null, null, 6);
        assertRechaza(400, () -> service.crear(producto("5000", 50), r, AHORA));
    }

    // ==================== crear: reglas de convivencia ====================

    @Test
    void crear_productoYaTieneOferta_lanza409() {
        when(ofertas.findByProductoIdAndEstadoAndFinAfter(PRODUCTO_ID, Oferta.Estado.ACTIVA, AHORA))
                .thenReturn(List.of(ofertaVigente(20, 2, null, 0)));

        assertRechaza(409, () -> service.crear(producto("5000", 50), req(30, 24), AHORA));
    }

    @Test
    void crear_tiendaEnElLimiteDeOfertas_lanza409() {
        when(ofertas.countByTiendaIdAndEstadoAndFinAfter(TIENDA_ID, Oferta.Estado.ACTIVA, AHORA))
                .thenReturn(3L);

        assertRechaza(409, () -> service.crear(producto("5000", 50), req(30, 24), AHORA));
    }

    @Test
    void crear_antesDeLaEspera_lanza409() {
        Oferta anterior = ofertaVigente(20, 2, null, 0);
        anterior.setFin(AHORA.minus(2, ChronoUnit.HOURS));
        when(ofertas.findFirstByProductoIdAndEstadoOrderByFinDesc(PRODUCTO_ID, Oferta.Estado.ACTIVA))
                .thenReturn(Optional.of(anterior));

        assertRechaza(409, () -> service.crear(producto("5000", 50), req(30, 24), AHORA));
    }

    @Test
    void crear_despuesDeLaEspera_funciona() {
        Oferta anterior = ofertaVigente(20, 2, null, 0);
        anterior.setFin(AHORA.minus(25, ChronoUnit.HOURS));
        when(ofertas.findFirstByProductoIdAndEstadoOrderByFinDesc(PRODUCTO_ID, Oferta.Estado.ACTIVA))
                .thenReturn(Optional.of(anterior));
        when(ofertas.save(any(Oferta.class))).thenAnswer(inv -> inv.getArgument(0));

        Oferta oferta = service.crear(producto("5000", 50), req(30, 24), AHORA);

        assertEquals(30, oferta.getPorcentaje());
    }

    // ==================== cancelar ====================

    @Test
    void cancelar_marcaComoCancelada() {
        Oferta oferta = ofertaVigente(30, 2, null, 0);
        when(ofertas.save(any(Oferta.class))).thenAnswer(inv -> inv.getArgument(0));

        Oferta resultado = service.cancelar(oferta);

        assertEquals(Oferta.Estado.CANCELADA, resultado.getEstado());
    }

    // ==================== aplicarOferta ====================

    @Test
    void aplicarOferta_sinOferta_noAplicaNada() {
        ReservaStockResponseDTO r = service.aplicarOferta(producto("5000", 50), 2, AHORA);

        assertNull(r.ofertaId());
        assertNull(r.precioOferta());
        assertEquals(0, r.unidadesEnOferta());
        verify(ofertas, never()).consumirCupo(any(), anyInt(), any(), any());
    }

    @Test
    void aplicarOferta_pedidoMayorAlLimite_aplicaSoloElLimite() {
        Oferta oferta = ofertaVigente(30, 2, null, 0);
        when(ofertas.findByProductoIdAndEstadoAndInicioLessThanEqualAndFinAfter(
                PRODUCTO_ID, Oferta.Estado.ACTIVA, AHORA, AHORA)).thenReturn(List.of(oferta));
        when(ofertas.consumirCupo(oferta.getId(), 2, AHORA, Oferta.Estado.ACTIVA)).thenReturn(1);

        ReservaStockResponseDTO r = service.aplicarOferta(producto("5000", 50), 3, AHORA);

        assertEquals(oferta.getId(), r.ofertaId());
        assertEquals(0, r.precioOferta().compareTo(new BigDecimal("3500")));
        assertEquals(2, r.unidadesEnOferta());
    }

    @Test
    void aplicarOferta_pedidoMenorAlLimite_aplicaLoPedido() {
        Oferta oferta = ofertaVigente(30, 5, null, 0);
        when(ofertas.findByProductoIdAndEstadoAndInicioLessThanEqualAndFinAfter(
                PRODUCTO_ID, Oferta.Estado.ACTIVA, AHORA, AHORA)).thenReturn(List.of(oferta));
        when(ofertas.consumirCupo(oferta.getId(), 1, AHORA, Oferta.Estado.ACTIVA)).thenReturn(1);

        ReservaStockResponseDTO r = service.aplicarOferta(producto("5000", 50), 1, AHORA);

        assertEquals(1, r.unidadesEnOferta());
    }

    @Test
    void aplicarOferta_cupoParcial_bajaLasUnidades() {
        Oferta oferta = ofertaVigente(30, 2, 10, 9);
        when(ofertas.findByProductoIdAndEstadoAndInicioLessThanEqualAndFinAfter(
                PRODUCTO_ID, Oferta.Estado.ACTIVA, AHORA, AHORA)).thenReturn(List.of(oferta));
        when(ofertas.consumirCupo(oferta.getId(), 2, AHORA, Oferta.Estado.ACTIVA)).thenReturn(0);
        when(ofertas.consumirCupo(oferta.getId(), 1, AHORA, Oferta.Estado.ACTIVA)).thenReturn(1);

        ReservaStockResponseDTO r = service.aplicarOferta(producto("5000", 50), 2, AHORA);

        assertEquals(1, r.unidadesEnOferta());
    }

    @Test
    void aplicarOferta_cupoAgotadoEnLaCarrera_cobraPrecioNormal() {
        Oferta oferta = ofertaVigente(30, 2, 10, 9);
        when(ofertas.findByProductoIdAndEstadoAndInicioLessThanEqualAndFinAfter(
                PRODUCTO_ID, Oferta.Estado.ACTIVA, AHORA, AHORA)).thenReturn(List.of(oferta));
        when(ofertas.consumirCupo(any(), anyInt(), any(), any())).thenReturn(0);

        ReservaStockResponseDTO r = service.aplicarOferta(producto("5000", 50), 2, AHORA);

        assertNull(r.ofertaId());
        assertEquals(0, r.unidadesEnOferta());
    }

    @Test
    void aplicarOferta_ofertaSinCupoRestante_seIgnora() {
        Oferta agotada = ofertaVigente(30, 2, 10, 10);
        when(ofertas.findByProductoIdAndEstadoAndInicioLessThanEqualAndFinAfter(
                PRODUCTO_ID, Oferta.Estado.ACTIVA, AHORA, AHORA)).thenReturn(List.of(agotada));

        ReservaStockResponseDTO r = service.aplicarOferta(producto("5000", 50), 2, AHORA);

        assertEquals(0, r.unidadesEnOferta());
        verify(ofertas, never()).consumirCupo(any(), anyInt(), any(), any());
    }

    // ==================== devolverCupo ====================

    @Test
    void devolverCupo_conOferta_devuelve() {
        UUID ofertaId = UUID.randomUUID();

        service.devolverCupo(ofertaId, 2);

        verify(ofertas).devolverCupo(ofertaId, 2);
    }

    @Test
    void devolverCupo_sinOferta_noHaceNada() {
        service.devolverCupo(null, 0);
        service.devolverCupo(UUID.randomUUID(), 0);

        verify(ofertas, never()).devolverCupo(any(), anyInt());
    }

    // ==================== precio ====================

    @Test
    void precioSobre_redondeaAPesosEnteros() {
        Oferta treinta = ofertaVigente(30, 2, null, 0);
        Oferta quince = ofertaVigente(15, 2, null, 0);

        assertEquals(0, treinta.precioSobre(new BigDecimal("5000")).compareTo(new BigDecimal("3500")));
        // 3333 * 0,85 = 2833,05 -> 2833
        assertEquals(0, quince.precioSobre(new BigDecimal("3333")).compareTo(new BigDecimal("2833")));
    }
}