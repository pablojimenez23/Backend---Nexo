package com.nexo.msusers.service;

import com.nexo.msusers.entity.CanjePuntos;
import com.nexo.msusers.entity.MovimientoPuntos;
import com.nexo.msusers.repository.CanjePuntosRepository;
import com.nexo.msusers.repository.MovimientoPuntosRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Calcula el saldo vigente aplicando el vencimiento.
 * Cada acreditación es un lote con su propia fecha de vencimiento; al canjear se gastan primero
 * los lotes más antiguos que aún no han vencido.
 */
@Service
public class SaldoPuntosService {

    public record Saldo(long disponible, long vencidos, long porVencer,
                        LocalDate proximoVencimiento, long puntosProximoVencimiento) {
    }

    private static final ZoneId ZONA_CHILE = ZoneId.of("America/Santiago");

    private final MovimientoPuntosRepository movimientos;
    private final CanjePuntosRepository canjes;

    @Value("${nexo.puntos.meses-vigencia:12}")
    private long mesesVigencia;

    /** Desde esta fecha rige el vencimiento; los puntos anteriores vencen contando desde este día. */
    @Value("${nexo.puntos.vigencia-desde:2026-12-01}")
    private String vigenciaDesde;

    @Value("${nexo.puntos.dias-aviso:30}")
    private long diasAviso;

    public SaldoPuntosService(MovimientoPuntosRepository movimientos, CanjePuntosRepository canjes) {
        this.movimientos = movimientos;
        this.canjes = canjes;
    }

    public Saldo calcular(UUID usuarioId) {
        return calcular(usuarioId, Instant.now());
    }

    public Saldo calcular(UUID usuarioId, Instant ahora) {
        List<MovimientoPuntos> lotes = movimientos.findByUsuarioIdOrderByCreadoEnAsc(usuarioId);
        long[] restante = lotes.stream().mapToLong(MovimientoPuntos::getPuntos).toArray();

        // Cada canje consume los lotes más antiguos que existían y seguían vigentes en ese momento.
        for (CanjePuntos canje : canjes.findByUsuarioIdAndEstadoOrderByCreadoEnAsc(usuarioId, CanjePuntos.Estado.ACTIVO)) {
            long porGastar = canje.getPuntos();
            for (int i = 0; i < lotes.size() && porGastar > 0; i++) {
                MovimientoPuntos lote = lotes.get(i);
                if (lote.getCreadoEn().isAfter(canje.getCreadoEn())) {
                    break;
                }
                if (!venceEn(lote.getCreadoEn()).isAfter(canje.getCreadoEn())) {
                    continue;
                }
                long usa = Math.min(porGastar, restante[i]);
                restante[i] -= usa;
                porGastar -= usa;
            }
        }

        long disponible = 0;
        long vencidos = 0;
        long porVencer = 0;
        Instant proximo = null;
        Instant limiteAviso = ahora.plus(diasAviso, ChronoUnit.DAYS);

        for (int i = 0; i < lotes.size(); i++) {
            if (restante[i] <= 0) {
                continue;
            }
            Instant vence = venceEn(lotes.get(i).getCreadoEn());
            if (!vence.isAfter(ahora)) {
                vencidos += restante[i];
                continue;
            }
            disponible += restante[i];
            if (proximo == null || vence.isBefore(proximo)) {
                proximo = vence;
            }
            if (!vence.isAfter(limiteAviso)) {
                porVencer += restante[i];
            }
        }

        long enProximo = 0;
        if (proximo != null) {
            for (int i = 0; i < lotes.size(); i++) {
                if (restante[i] > 0 && venceEn(lotes.get(i).getCreadoEn()).equals(proximo)) {
                    enProximo += restante[i];
                }
            }
        }

        LocalDate ultimoDia = proximo == null ? null : proximo.minusSeconds(1).atZone(ZONA_CHILE).toLocalDate();
        return new Saldo(disponible, vencidos, porVencer, ultimoDia, enProximo);
    }

    /** Instante en que vence un lote: al terminar el día (hora de Chile) en que se cumplen los meses de vigencia. */
    public Instant venceEn(Instant acreditado) {
        Instant desde = LocalDate.parse(vigenciaDesde).atStartOfDay(ZONA_CHILE).toInstant();
        Instant base = acreditado.isBefore(desde) ? desde : acreditado;
        LocalDate ultimoDia = base.atZone(ZONA_CHILE).toLocalDate().plusMonths(mesesVigencia);
        return ultimoDia.plusDays(1).atStartOfDay(ZONA_CHILE).toInstant();
    }
}