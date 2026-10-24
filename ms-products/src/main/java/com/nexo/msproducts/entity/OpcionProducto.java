package com.nexo.msproducts.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Una opción dentro de un grupo ("Delgada", "Queso extra"), con su recargo si lo tiene. No tiene stock propio. */
@Entity
@Table(name = "opciones_producto", indexes = @Index(name = "idx_opcion_grupo", columnList = "grupo_id"))
public class OpcionProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "precio_adicional", nullable = false)
    private BigDecimal precioAdicional = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean disponible = true;

    @Column(nullable = false)
    private int orden = 0;

    public OpcionProducto() {
    }

    public UUID getId() { return id; }
    public UUID getGrupoId() { return grupoId; }
    public void setGrupoId(UUID grupoId) { this.grupoId = grupoId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public BigDecimal getPrecioAdicional() { return precioAdicional; }
    public void setPrecioAdicional(BigDecimal precioAdicional) { this.precioAdicional = precioAdicional; }
    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
}