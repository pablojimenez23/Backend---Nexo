package com.nexo.msusers.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "conductores")
public class Conductor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Relación uno a uno: todo conductor es un usuario, pero no todo usuario es conductor.
    // El dueño de la relación es Conductor, ya que no todos los USUARIOS tienen fila acá.
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

    public Conductor() {
    }

    @PrePersist
    protected void alCrear() {
        if (this.estado == null) {
            this.estado = Estado.DISPONIBLE;
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

    public enum Estado {
        DISPONIBLE, OCUPADO, INACTIVO
    }
}