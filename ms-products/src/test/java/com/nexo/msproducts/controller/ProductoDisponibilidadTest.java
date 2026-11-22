package com.nexo.msproducts.controller;

import com.nexo.msproducts.client.TiendaClient;
import com.nexo.msproducts.dto.ProductoResponseDTO;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.repository.ProductoRepository;
import com.nexo.msproducts.repository.VarianteProductoRepository;
import com.nexo.msproducts.service.OfertaService;
import com.nexo.msproducts.service.ProductoDetalleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class ProductoDisponibilidadTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private TiendaClient tiendaClient;
    @Mock private OfertaService ofertaService;
    @Mock private ProductoDetalleService detalle;
    @Mock private VarianteProductoRepository varianteRepository;

    private ProductoController controller;
    private final UUID productoId = UUID.randomUUID();
    private final UUID tiendaId = UUID.randomUUID();
    private final UUID duenoId = UUID.randomUUID();
    private Producto producto;

    @BeforeEach
    void preparar() {
        controller = new ProductoController(productoRepository, tiendaClient, ofertaService, detalle, varianteRepository);
        producto = new Producto();
        producto.setTiendaId(tiendaId);
    }

    private JwtAuthenticationToken token(UUID usuario, String... grupos) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", usuario.toString())
                .claim("cognito:groups", List.of(grupos)).build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void elDuenoDeLaTiendaCambiaLaDisponibilidad() {
        when(productoRepository.findById(productoId)).thenReturn(Optional.of(producto));
        when(tiendaClient.obtenerOwnerId(tiendaId)).thenReturn(duenoId);
        when(productoRepository.save(producto)).thenReturn(producto);
        when(detalle.responder(any(Producto.class), any())).thenReturn(mock(ProductoResponseDTO.class));

        assertEquals(200, controller.cambiarDisponibilidad(productoId, false, token(duenoId, "TIENDA")).getStatusCode().value());
        verify(productoRepository).save(producto);
    }

    @Test
    void otraTiendaRecibe403SinCambios() {
        when(productoRepository.findById(productoId)).thenReturn(Optional.of(producto));
        when(tiendaClient.obtenerOwnerId(tiendaId)).thenReturn(duenoId);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.cambiarDisponibilidad(productoId, false, token(UUID.randomUUID(), "TIENDA")));

        assertEquals(403, e.getStatusCode().value());
        verify(productoRepository, never()).save(any());
    }

    @Test
    void elAdministradorNoNecesitaSerDueno() {
        when(productoRepository.findById(productoId)).thenReturn(Optional.of(producto));
        when(productoRepository.save(producto)).thenReturn(producto);
        when(detalle.responder(any(Producto.class), any())).thenReturn(mock(ProductoResponseDTO.class));

        controller.cambiarDisponibilidad(productoId, true, token(UUID.randomUUID(), "ADMIN"));

        verifyNoInteractions(tiendaClient);
        verify(productoRepository).save(producto);
    }

    @Test
    void unProductoInexistenteResponde404() {
        when(productoRepository.findById(productoId)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.cambiarDisponibilidad(productoId, true, token(duenoId, "TIENDA")));
        assertEquals(404, e.getStatusCode().value());
    }
}