package com.nexo.msusers.service;

import com.nexo.msusers.entity.Notificacion;
import com.nexo.msusers.repository.MovimientoPuntosRepository;
import com.nexo.msusers.repository.NotificacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Cada día a las 09:00 (hora de Chile) avisa a los clientes cuyo próximo vencimiento de puntos
 * cae exactamente dentro de 30 o de 7 días. Al comparar el día exacto, cada aviso se envía una sola vez.
 */
@Service
public class AvisoVencimientoJob {

    private static final Logger log = LoggerFactory.getLogger(AvisoVencimientoJob.class);
    private static final ZoneId ZONA_CHILE = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MovimientoPuntosRepository movimientos;
    private final SaldoPuntosService saldos;
    private final NotificacionRepository notificaciones;

    public AvisoVencimientoJob(MovimientoPuntosRepository movimientos, SaldoPuntosService saldos,
                               NotificacionRepository notificaciones) {
        this.movimientos = movimientos;
        this.saldos = saldos;
        this.notificaciones = notificaciones;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Santiago")
    public void avisar() {
        LocalDate hoy = LocalDate.now(ZONA_CHILE);
        int avisados = 0;
        for (UUID usuarioId : movimientos.usuariosConPuntos()) {
            try {
                if (avisarUsuario(usuarioId, hoy)) {
                    avisados++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo avisar el vencimiento al usuario {}", usuarioId, e);
            }
        }
        log.info("Aviso de vencimiento de puntos: {} usuarios notificados", avisados);
    }

    boolean avisarUsuario(UUID usuarioId, LocalDate hoy) {
        SaldoPuntosService.Saldo s = saldos.calcular(usuarioId);
        if (s.proximoVencimiento() == null) {
            return false;
        }
        long dias = ChronoUnit.DAYS.between(hoy, s.proximoVencimiento());
        if (dias != 30 && dias != 7) {
            return false;
        }
        Notificacion n = new Notificacion();
        n.setUsuarioId(usuarioId);
        n.setTitulo(dias == 7 ? "Tus puntos vencen en 7 días" : "Tus puntos vencen pronto");
        n.setMensaje("Tenés " + s.puntosProximoVencimiento() + " puntos que vencen el "
                + s.proximoVencimiento().format(FORMATO) + ". Usalos en tu próximo pedido.");
        notificaciones.save(n);
        return true;
    }
}