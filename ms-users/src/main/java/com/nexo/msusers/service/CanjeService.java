package com.nexo.msusers.service;

import com.nexo.msusers.entity.CanjePuntos;
import com.nexo.msusers.repository.CanjePuntosRepository;
import com.nexo.msusers.repository.MovimientoPuntosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/** Saldo de puntos = acumulados por pedidos completados - canjes vigentes. */
@Service
public class CanjeService {

    private static final Logger log = LoggerFactory.getLogger(CanjeService.class);

    private final MovimientoPuntosRepository movimientos;
    private final CanjePuntosRepository canjes;

    public CanjeService(MovimientoPuntosRepository movimientos, CanjePuntosRepository canjes) {
        this.movimientos = movimientos;
        this.canjes = canjes;
    }

    public long saldoDisponible(UUID usuarioId) {
        return movimientos.saldoDe(usuarioId) - canjes.totalActivoDe(usuarioId);
    }

    @Transactional
    public CanjePuntos canjear(UUID usuarioId, long puntos) {
        if (puntos <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los puntos a canjear deben ser mayores a cero");
        }
        if (saldoDisponible(usuarioId) < puntos) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Puntos insuficientes");
        }
        CanjePuntos canje = new CanjePuntos();
        canje.setUsuarioId(usuarioId);
        canje.setPuntos(puntos);
        CanjePuntos guardado = canjes.save(canje);
        log.info("Se canjearon {} puntos del cliente {} (canje {})", puntos, usuarioId, guardado.getId());
        return guardado;
    }

    /** Idempotente: revertir un canje ya revertido no cambia nada. */
    @Transactional
    public CanjePuntos revertir(UUID canjeId) {
        CanjePuntos canje = canjes.findById(canjeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Canje no encontrado"));
        if (canje.getEstado() == CanjePuntos.Estado.ACTIVO) {
            canje.setEstado(CanjePuntos.Estado.REVERTIDO);
            canje.setRevertidoEn(Instant.now());
            canjes.save(canje);
            log.info("Se devolvieron {} puntos al cliente {} (canje {} revertido)",
                    canje.getPuntos(), canje.getUsuarioId(), canje.getId());
        }
        return canje;
    }
}