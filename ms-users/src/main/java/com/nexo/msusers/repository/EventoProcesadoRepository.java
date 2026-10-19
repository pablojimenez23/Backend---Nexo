package com.nexo.msusers.repository;

import com.nexo.msusers.entity.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, String> {

    /** Devuelve 1 si el evento era nuevo y 0 si ya estaba registrado (duplicado). */
    @Modifying
    @Query(value = "INSERT IGNORE INTO eventos_procesados (clave, procesado_en) "
            + "VALUES (:clave, UTC_TIMESTAMP(6))", nativeQuery = true)
    int registrar(@Param("clave") String clave);
}