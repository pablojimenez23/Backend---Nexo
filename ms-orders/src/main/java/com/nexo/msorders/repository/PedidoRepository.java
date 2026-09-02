package com.nexo.msorders.repository;

import com.nexo.msorders.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    List<Pedido> findByClienteId(UUID clienteId);

    List<Pedido> findByTiendaId(UUID tiendaId);

    /**
     * UPDATE condicional: solo asigna el conductor si el pedido sigue
     * en READY y sin conductor asignado. Evita que dos conductores
     * tomen el mismo pedido al mismo tiempo.
     * Devuelve cuántas filas se modificaron (0 = ya lo tomó otro).
     */
    @Modifying
    @Query("UPDATE Pedido p SET p.conductorId = :conductorId, p.estado = 'DELIVERING' " +
           "WHERE p.id = :id AND p.conductorId IS NULL AND p.estado = 'READY'")
    int asignarConductor(@Param("id") UUID id, @Param("conductorId") UUID conductorId);

}