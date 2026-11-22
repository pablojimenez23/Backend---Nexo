package com.nexo.msproducts.controller;

import com.nexo.msproducts.client.TiendaClient;
import com.nexo.msproducts.dto.OfertaRequestDTO;
import com.nexo.msproducts.dto.OfertaResponseDTO;
import com.nexo.msproducts.entity.Oferta;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.repository.OfertaRepository;
import com.nexo.msproducts.repository.ProductoRepository;
import com.nexo.msproducts.service.OfertaService;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfertaControllerTest {

    @Mock private OfertaService ofertaService;
    @Mock private OfertaRepository ofertaRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private TiendaClient tiendaClient;
    @Mock private JwtAuthenticationToken auth;
    @Mock private Jwt jwt;

    private OfertaController controller;

    private static final UUID TIENDA_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID PRODUCTO_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new OfertaController(ofertaService, ofertaRepository, productoRepository, tiendaClient);
        lenient().when(auth.getPrincipal()).thenReturn(jwt);
    }

    private void simularUsuario(UUID sub) {
        lenient().when(jwt.getClaimAsString("sub")).thenReturn(sub.toString());
        lenient().when(jwt.getClaimAsStringList("cognito:groups")).thenReturn(List.of("TIENDA"));
    }

    private Producto producto() {
        Producto p = new Producto();
        ReflectionTestUtils.setField(p, "id", PRODUCTO_ID);
        p.setTiendaId(TIENDA_ID);
        p.setNombre("Producto Test");
        p.setPrecio(new BigDecimal("5000"));
        p.setStock(20);
        return p;
    }

    private Oferta oferta() {
        Oferta o = new Oferta();
        o.setProductoId(PRODUCTO_ID);
        o.setTiendaId(TIENDA_ID);
        o.setPorcentaje(30);
        o.setInicio(Instant.now());
        o.setFin(Instant.now().plusSeconds(3600));
        o.setLimitePorPedido(2);
        return o;
    }

    private OfertaRequestDTO request() {
        return new OfertaRequestDTO(PRODUCTO_ID, 30, 24, null, null, null);
    }

    // ==================== crear ====================

    @Test
    void crear_dueñoReal_creaOferta() {
        simularUsuario(OWNER_ID);
        Producto producto = producto();
        OfertaRequestDTO request = request();
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(producto));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(ofertaService.crear(eq(producto), eq(request), any(Instant.class))).thenReturn(oferta());

        ResponseEntity<OfertaResponseDTO> response = controller.crear(request, auth);

        assertEquals(201, response.getStatusCode().value());
        assertEquals(0, response.getBody().precioOferta().compareTo(new BigDecimal("3500")));
    }

    @Test
    void crear_noEsElDueño_lanza403() {
        simularUsuario(UUID.randomUUID());
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(producto()));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.crear(request(), auth));

        assertEquals(403, e.getStatusCode().value());
        verify(ofertaService, never()).crear(any(), any(), any());
    }

    @Test
    void crear_productoNoExiste_lanza404() {
        simularUsuario(OWNER_ID);
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.crear(request(), auth));

        assertEquals(404, e.getStatusCode().value());
    }

    @Test
    void crear_sinProducto_lanza400() {
        simularUsuario(OWNER_ID);
        OfertaRequestDTO sinProducto = new OfertaRequestDTO(null, 30, 24, null, null, null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.crear(sinProducto, auth));

        assertEquals(400, e.getStatusCode().value());
    }

    // ==================== cancelar ====================

    @Test
    void cancelar_dueñoReal_cancela() {
        simularUsuario(OWNER_ID);
        UUID id = UUID.randomUUID();
        Oferta existente = oferta();
        Oferta cancelada = oferta();
        cancelada.setEstado(Oferta.Estado.CANCELADA);

        when(ofertaRepository.findById(id)).thenReturn(Optional.of(existente));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(ofertaService.cancelar(existente)).thenReturn(cancelada);
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(producto()));

        OfertaResponseDTO resultado = controller.cancelar(id, auth);

        assertEquals("CANCELADA", resultado.estado());
    }

    @Test
    void cancelar_noEsElDueño_lanza403() {
        simularUsuario(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        when(ofertaRepository.findById(id)).thenReturn(Optional.of(oferta()));
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);

        assertThrows(ResponseStatusException.class, () -> controller.cancelar(id, auth));
        verify(ofertaService, never()).cancelar(any());
    }

    @Test
    void cancelar_noExiste_lanza404() {
        simularUsuario(OWNER_ID);
        UUID id = UUID.randomUUID();
        when(ofertaRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.cancelar(id, auth));

        assertEquals(404, e.getStatusCode().value());
    }

    // ==================== deTienda ====================

    @Test
    void deTienda_noEsElDueño_lanza403() {
        simularUsuario(UUID.randomUUID());
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);

        assertThrows(ResponseStatusException.class, () -> controller.deTienda(TIENDA_ID, auth));
    }

    @Test
    void deTienda_dueñoReal_devuelveSusOfertas() {
        simularUsuario(OWNER_ID);
        when(tiendaClient.obtenerOwnerId(TIENDA_ID)).thenReturn(OWNER_ID);
        when(productoRepository.findByTiendaId(TIENDA_ID)).thenReturn(List.of(producto()));
        when(ofertaService.pendientesDeTienda(eq(TIENDA_ID), any(Instant.class))).thenReturn(List.of(oferta()));

        List<OfertaResponseDTO> resultado = controller.deTienda(TIENDA_ID, auth);

        assertEquals(1, resultado.size());
        assertEquals(0, resultado.get(0).precioOferta().compareTo(new BigDecimal("3500")));
    }
}