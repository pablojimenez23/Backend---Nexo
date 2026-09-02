package com.nexo.msproducts.repository;

import com.nexo.msproducts.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {

    List<Producto> findByTiendaId(UUID tiendaId);

    /**
     * UPDATE condicional: solo descuenta stock si hay suficiente disponible.
     * Evita que se venda más de lo que existe cuando varios clientes compran
     * al mismo tiempo. Devuelve cuántas filas se modificaron (0 = sin stock).
     */
    @Modifying
    @Query("UPDATE Producto p SET p.stock = p.stock - :cantidad " +
           "WHERE p.id = :id AND p.stock >= :cantidad")
    int reservarStock(@Param("id") UUID id, @Param("cantidad") int cantidad);

    /**
     * Devuelve stock al producto — se usa cuando ms-orders cancela un pedido
     * y hay que revertir la reserva hecha antes.
     */
    @Modifying
    @Query("UPDATE Producto p SET p.stock = p.stock + :cantidad WHERE p.id = :id")
    void revertirStock(@Param("id") UUID id, @Param("cantidad") int cantidad);

}