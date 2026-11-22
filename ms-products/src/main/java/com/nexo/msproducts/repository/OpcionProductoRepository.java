package com.nexo.msproducts.repository;

import com.nexo.msproducts.entity.OpcionProducto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OpcionProductoRepository extends JpaRepository<OpcionProducto, UUID> {

    List<OpcionProducto> findByGrupoIdInOrderByOrdenAsc(Collection<UUID> grupoIds);

    void deleteByGrupoIdIn(Collection<UUID> grupoIds);
}