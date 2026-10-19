package com.nexo.msusers.repository;

import com.nexo.msusers.entity.MovimientoPuntos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MovimientoPuntosRepository extends JpaRepository<MovimientoPuntos, UUID> {

    boolean existsByPedidoId(UUID pedidoId);

    @Query("select coalesce(sum(m.puntos), 0) from MovimientoPuntos m where m.usuarioId = :usuarioId")
    long saldoDe(@Param("usuarioId") UUID usuarioId);
}