package com.nexo.msusers.repository;

import com.nexo.msusers.entity.CanjePuntos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CanjePuntosRepository extends JpaRepository<CanjePuntos, UUID> {

    @Query("select coalesce(sum(c.puntos), 0) from CanjePuntos c "
            + "where c.usuarioId = :usuarioId and c.estado = :estado")
    long totalPorEstado(@Param("usuarioId") UUID usuarioId, @Param("estado") CanjePuntos.Estado estado);

    /** Puntos que el cliente ya gastó y siguen vigentes. */
    default long totalActivoDe(UUID usuarioId) {
        return totalPorEstado(usuarioId, CanjePuntos.Estado.ACTIVO);
    }
}