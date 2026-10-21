package com.nexo.msusers.repository;

import com.nexo.msusers.entity.MovimientoPuntos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MovimientoPuntosRepository extends JpaRepository<MovimientoPuntos, UUID> {

    boolean existsByPedidoId(UUID pedidoId);

    @Query("select coalesce(sum(m.puntos), 0) from MovimientoPuntos m where m.usuarioId = :usuarioId")
    long saldoDe(@Param("usuarioId") UUID usuarioId);

    /** Total de puntos emitidos a todos los clientes. */
    @Query("select coalesce(sum(m.puntos), 0) from MovimientoPuntos m")
    long totalEmitidos();

    /** Cada fila es [usuarioId, puntos acumulados]. */
    @Query("select m.usuarioId, sum(m.puntos) from MovimientoPuntos m group by m.usuarioId")
    List<Object[]> acumuladosPorUsuario();
}