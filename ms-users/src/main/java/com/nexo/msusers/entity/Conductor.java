package com.nexo.msusers.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conductores")
public class Conductor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String vehiculo;

    @Column(nullable = false)
    private String patente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    public Conductor() {
    }

    @PrePersist
    protected void alCrear() {
        this.creadoEn = Instant.now();
        if (this.estado == null) {
            this.estado = Estado.PENDIENTE_APROBACION;
        }
    }

    public UUID getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getVehiculo() { return vehiculo; }
    public void setVehiculo(String vehiculo) { this.vehiculo = vehiculo; }
    public String getPatente() { return patente; }
    public void setPatente(String patente) { this.patente = patente; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }
    public Instant getCreadoEn() { return creadoEn; }

    public enum Estado {
        PENDIENTE_APROBACION, DISPONIBLE, OCUPADO, INACTIVO, RECHAZADO
    }
}