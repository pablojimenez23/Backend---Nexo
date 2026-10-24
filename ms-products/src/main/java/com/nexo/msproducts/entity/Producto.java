package com.nexo.msproducts.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tienda_id", nullable = false)
    private UUID tiendaId;

    @Column(name = "categoria_id")
    private UUID categoriaId;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;

    /** Si el producto tiene variantes, es el precio más bajo de ellas ("desde $X"). */
    @Column(nullable = false)
    private BigDecimal precio;

    /** Si el producto tiene variantes, es la suma del stock de todas ellas. */
    @Column(nullable = false)
    private Integer stock;

    @Column(name = "imagen_url")
    private String imagenUrl;

    @Column(nullable = false)
    private boolean disponible = true;

    /** Tipo de producto (pizza, sushi...). Los productos antiguos quedan sin tipo y se tratan como OTRO. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    private TipoProducto tipo = TipoProducto.OTRO;

    /** Datos informativos del tipo (ingredientes, alérgenos, etiquetas de dieta...) como JSON. */
    @Column(name = "atributos", columnDefinition = "TEXT")
    private String atributos;

    /** La tienda lo marca para mostrarlo como "Popular". */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean destacado = false;

    /** Para cuántas personas rinde ("ideal para 2"). */
    private Integer personas;

    public Producto() {
    }

    public UUID getId() { return id; }
    public UUID getTiendaId() { return tiendaId; }
    public void setTiendaId(UUID tiendaId) { this.tiendaId = tiendaId; }
    public UUID getCategoriaId() { return categoriaId; }
    public void setCategoriaId(UUID categoriaId) { this.categoriaId = categoriaId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
    public TipoProducto getTipo() { return tipo == null ? TipoProducto.OTRO : tipo; }
    public void setTipo(TipoProducto tipo) { this.tipo = tipo; }
    public String getAtributos() { return atributos; }
    public void setAtributos(String atributos) { this.atributos = atributos; }
    public boolean isDestacado() { return destacado; }
    public void setDestacado(boolean destacado) { this.destacado = destacado; }
    public Integer getPersonas() { return personas; }
    public void setPersonas(Integer personas) { this.personas = personas; }
}