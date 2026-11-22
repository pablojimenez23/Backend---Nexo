package com.nexo.msusers.service;

import com.nexo.msusers.entity.CanjePuntos;
import com.nexo.msusers.repository.CanjePuntosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Saldo y reglas del canje de puntos. Sin base de datos. */
class CanjeServiceTest {

    private static final UUID CLIENTE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private CanjePuntosRepository canjes;
    private SaldoPuntosService saldos;
    private CanjeService servicio;

    @BeforeEach
    void preparar() {
        canjes = mock(CanjePuntosRepository.class);
        saldos = mock(SaldoPuntosService.class);
        servicio = new CanjeService(canjes, saldos);
        when(canjes.save(any(CanjePuntos.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void saldo(long disponible, long vencidos) {
        when(saldos.calcular(CLIENTE))
                .thenReturn(new SaldoPuntosService.Saldo(disponible, vencidos, 0L, null, 0L));
    }

    @Test
    @DisplayName("Saldo disponible = lo que informa el cálculo con vencimiento")
    void saldo_usaElCalculoConVencimiento() {
        saldo(61L, 0L);
        assertEquals(61L, servicio.saldoDisponible(CLIENTE));
    }

    @Test
    @DisplayName("Canje con saldo suficiente: guarda el canje por los puntos pedidos")
    void canjear_conSaldo_guarda() {
        saldo(101L, 0L);

        CanjePuntos canje = servicio.canjear(CLIENTE, 60L);

        assertEquals(CLIENTE, canje.getUsuarioId());
        assertEquals(60L, canje.getPuntos());
    }

    @Test
    @DisplayName("Canje sin saldo suficiente: 409 y no se guarda nada")
    void canjear_sinSaldo_conflicto() {
        saldo(50L, 0L);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.canjear(CLIENTE, 60L));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(canjes, never()).save(any(CanjePuntos.class));
    }

    @Test
    @DisplayName("Puntos vencidos no se pueden canjear: 409 y no se guarda nada")
    void canjear_puntosVencidos_conflicto() {
        saldo(0L, 100L);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.canjear(CLIENTE, 60L));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(canjes, never()).save(any(CanjePuntos.class));
    }

    @Test
    @DisplayName("Canje de cero o negativo: 400")
    void canjear_puntosInvalidos_badRequest() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.canjear(CLIENTE, 0L));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    @DisplayName("Revertir un canje vigente lo marca como REVERTIDO")
    void revertir_marcaRevertido() {
        CanjePuntos canje = new CanjePuntos();
        canje.setUsuarioId(CLIENTE);
        canje.setPuntos(60L);
        canje.setEstado(CanjePuntos.Estado.ACTIVO);
        UUID id = UUID.randomUUID();
        when(canjes.findById(id)).thenReturn(Optional.of(canje));

        CanjePuntos resultado = servicio.revertir(id);

        assertEquals(CanjePuntos.Estado.REVERTIDO, resultado.getEstado());
        verify(canjes).save(canje);
    }

    @Test
    @DisplayName("Revertir dos veces es idempotente: la segunda no cambia nada")
    void revertir_esIdempotente() {
        CanjePuntos canje = new CanjePuntos();
        canje.setEstado(CanjePuntos.Estado.REVERTIDO);
        UUID id = UUID.randomUUID();
        when(canjes.findById(id)).thenReturn(Optional.of(canje));

        servicio.revertir(id);

        verify(canjes, never()).save(any(CanjePuntos.class));
    }

    @Test
    @DisplayName("Revertir un canje que no existe: 404")
    void revertir_noExiste_404() {
        UUID id = UUID.randomUUID();
        when(canjes.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.revertir(id));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }
}