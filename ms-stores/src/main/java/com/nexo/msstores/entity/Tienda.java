package com.nexo.msstores.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tiendas")
public class Tienda {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;

    @Column(nullable = false)
    private String direccion;

    private BigDecimal latitud;
    private BigDecimal longitud;

    @Column(name = "logo_url")
    private String logoUrl;

    private String horario;

    @Column(name = "monto_minimo")
    private BigDecimal montoMinimo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaTienda categoria;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    public Tienda() {
    }

    @PrePersist
    protected void alCrear() {
        this.creadoEn = Instant.now();
        if (this.estado == null) {
            this.estado = Estado.PENDING;
        }
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public BigDecimal getLatitud() { return latitud; }
    public void setLatitud(BigDecimal latitud) { this.latitud = latitud; }
    public BigDecimal getLongitud() { return longitud; }
    public void setLongitud(BigDecimal longitud) { this.longitud = longitud; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getHorario() { return horario; }
    public void setHorario(String horario) { this.horario = horario; }
    public BigDecimal getMontoMinimo() { return montoMinimo; }
    public void setMontoMinimo(BigDecimal montoMinimo) { this.montoMinimo = montoMinimo; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public CategoriaTienda getCategoria() { return categoria; }
    public void setCategoria(CategoriaTienda categoria) { this.categoria = categoria; }
    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }
    public Instant getCreadoEn() { return creadoEn; }

    public enum Estado {
        PENDING, APPROVED, REJECTED
    }

    public enum CategoriaTienda {
        RESTAURANTE, BOTILLERIA, MERCADO
    }
}