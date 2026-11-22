package com.nexo.msproducts.repository;

import com.nexo.msproducts.entity.GrupoOpciones;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GrupoOpcionesRepository extends JpaRepository<GrupoOpciones, UUID> {

    List<GrupoOpciones> findByProductoIdOrderByOrdenAsc(UUID productoId);

    List<GrupoOpciones> findByProductoIdInOrderByOrdenAsc(Collection<UUID> productoIds);

    void deleteByProductoId(UUID productoId);

    void deleteByProductoIdIn(Collection<UUID> productoIds);
}