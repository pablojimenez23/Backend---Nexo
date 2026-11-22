package com.nexo.msproducts.repository;

import com.nexo.msproducts.entity.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfertaRepository extends JpaRepository<Oferta, UUID> {

    /** Ofertas de un producto que ya empezaron y todavía no terminan (el cupo se filtra en el servicio). */
    List<Oferta> findByProductoIdAndEstadoAndInicioLessThanEqualAndFinAfter(
            UUID productoId, Oferta.Estado estado, Instant inicio, Instant fin);

    List<Oferta> findByTiendaIdAndEstadoAndInicioLessThanEqualAndFinAfter(
            UUID tiendaId, Oferta.Estado estado, Instant inicio, Instant fin);

    /** Ofertas activas o programadas (que todavía no terminan). */
    List<Oferta> findByProductoIdAndEstadoAndFinAfter(UUID productoId, Oferta.Estado estado, Instant fin);

    List<Oferta> findByTiendaIdAndEstadoAndFinAfter(UUID tiendaId, Oferta.Estado estado, Instant fin);

    long countByTiendaIdAndEstadoAndFinAfter(UUID tiendaId, Oferta.Estado estado, Instant fin);

    /** Última oferta que terminó (o termina) para un producto; sirve para la espera entre ofertas. */
    Optional<Oferta> findFirstByProductoIdAndEstadoOrderByFinDesc(UUID productoId, Oferta.Estado estado);

    /**
     * UPDATE condicional: solo consume cupo si la oferta está vigente y quedan unidades.
     * Devuelve 1 si consumió, 0 si no (cupo agotado, vencida o cancelada).
     */
    @Modifying
    @Query("UPDATE Oferta o SET o.unidadesVendidas = o.unidadesVendidas + :cantidad " +
           "WHERE o.id = :id AND o.estado = :estado AND o.inicio <= :ahora AND o.fin > :ahora " +
           "AND (o.cupo IS NULL OR o.unidadesVendidas + :cantidad <= o.cupo)")
    int consumirCupo(@Param("id") UUID id,
                     @Param("cantidad") int cantidad,
                     @Param("ahora") Instant ahora,
                     @Param("estado") Oferta.Estado estado);

    /** Devuelve cupo cuando un pedido se rechaza antes de crearse. */
    @Modifying
    @Query("UPDATE Oferta o SET o.unidadesVendidas = o.unidadesVendidas - :cantidad " +
           "WHERE o.id = :id AND o.unidadesVendidas >= :cantidad")
    int devolverCupo(@Param("id") UUID id, @Param("cantidad") int cantidad);
}