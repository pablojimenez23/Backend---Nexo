package com.nexo.msorders.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "items_pedido")
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    /** Copia del nombre del producto al momento de la compra. */
    @Column(name = "nombre_producto")
    private String nombreProducto;

    /** Opción elegida (tamaño, formato...), si el producto tiene variantes. */
    @Column(name = "variante_id")
    private UUID varianteId;

    @Column(name = "variante_nombre")
    private String varianteNombre;

    /** Opciones elegidas como JSON: [{"grupo":"Masa","nombre":"Delgada","precio":0}]. */
    @Column(name = "opciones_detalle", length = 1000)
    private String opcionesDetalle;

    /** Lo que suman las opciones con recargo por cada unidad (ya incluido en precioUnitario). */
    @Column(name = "opciones_unitario")
    private BigDecimal opcionesUnitario;

    /** Aclaración del cliente para la tienda ("sin cebolla"). */
    @Column(length = 140)
    private String nota;

    @Column(nullable = false)
    private Integer cantidad;

    /** Precio de una unidad: tamaño (con oferta si corresponde) más opciones con recargo. */
    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    public ItemPedido() {
    }

    public UUID getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }
    public UUID getProductoId() { return productoId; }
    public void setProductoId(UUID productoId) { this.productoId = productoId; }
    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }
    public UUID getVarianteId() { return varianteId; }
    public void setVarianteId(UUID varianteId) { this.varianteId = varianteId; }
    public String getVarianteNombre() { return varianteNombre; }
    public void setVarianteNombre(String varianteNombre) { this.varianteNombre = varianteNombre; }
    public String getOpcionesDetalle() { return opcionesDetalle; }
    public void setOpcionesDetalle(String opcionesDetalle) { this.opcionesDetalle = opcionesDetalle; }
    public BigDecimal getOpcionesUnitario() { return opcionesUnitario; }
    public void setOpcionesUnitario(BigDecimal opcionesUnitario) { this.opcionesUnitario = opcionesUnitario; }
    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
}