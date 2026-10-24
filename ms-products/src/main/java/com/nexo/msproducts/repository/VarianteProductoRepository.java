package com.nexo.msproducts.repository;

import com.nexo.msproducts.entity.VarianteProducto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VarianteProductoRepository extends JpaRepository<VarianteProducto, UUID> {

    List<VarianteProducto> findByProductoIdOrderByOrdenAsc(UUID productoId);

    List<VarianteProducto> findByProductoIdInOrderByOrdenAsc(Collection<UUID> productoIds);

    Optional<VarianteProducto> findByIdAndProductoId(UUID id, UUID productoId);

    boolean existsByProductoId(UUID productoId);

    void deleteByProductoId(UUID productoId);

    void deleteByProductoIdIn(Collection<UUID> productoIds);

    /** Descuenta stock de la variante solo si está disponible y alcanza. Devuelve 1 si lo hizo, 0 si no. */
    @Modifying
    @Query("UPDATE VarianteProducto v SET v.stock = v.stock - :cantidad " +
           "WHERE v.id = :id AND v.productoId = :productoId AND v.disponible = true AND v.stock >= :cantidad")
    int reservarStock(@Param("id") UUID id,
                      @Param("productoId") UUID productoId,
                      @Param("cantidad") int cantidad);

    /** Devuelve stock a la variante. Devuelve 0 si la variante ya no existe. */
    @Modifying
    @Query("UPDATE VarianteProducto v SET v.stock = v.stock + :cantidad " +
           "WHERE v.id = :id AND v.productoId = :productoId")
    int revertirStock(@Param("id") UUID id,
                      @Param("productoId") UUID productoId,
                      @Param("cantidad") int cantidad);
}