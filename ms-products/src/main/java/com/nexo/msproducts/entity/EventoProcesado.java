package com.nexo.msproducts.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Registro de eventos ya procesados (clave = consumidor + ":" + eventoId) para ser idempotentes. */
@Entity
@Table(name = "eventos_procesados")
public class EventoProcesado {

    @Id
    @Column(length = 100)
    private String clave;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {
    }

    public String getClave() { return clave; }
    public Instant getProcesadoEn() { return procesadoEn; }
}