package com.nexo.msusers.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    public Usuario() {
    }

    @PrePersist
    protected void alCrear() {
        this.creadoEn = Instant.now();
        if (this.estado == null) {
            this.estado = Estado.ACTIVO;
        }
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public Instant getCreadoEn() { return creadoEn; }

    public enum Rol {
        CLIENTE, TIENDA, ADMIN, CONDUCTOR
    }

    public enum Estado {
        ACTIVO, INACTIVO
    }
}