package com.nexo.msproducts.service;

import com.nexo.msproducts.dto.GrupoOpcionesDTO;
import com.nexo.msproducts.dto.OpcionDTO;
import com.nexo.msproducts.dto.ProductoRequestDTO;
import com.nexo.msproducts.dto.ProductoResponseDTO;
import com.nexo.msproducts.dto.VarianteDTO;
import com.nexo.msproducts.entity.GrupoOpciones;
import com.nexo.msproducts.entity.Oferta;
import com.nexo.msproducts.entity.OpcionProducto;
import com.nexo.msproducts.entity.Producto;
import com.nexo.msproducts.entity.TipoProducto;
import com.nexo.msproducts.entity.VarianteProducto;
import com.nexo.msproducts.repository.GrupoOpcionesRepository;
import com.nexo.msproducts.repository.OpcionProductoRepository;
import com.nexo.msproducts.repository.VarianteProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Todo lo que tiene que ver con el detalle de un producto: validar lo que manda la tienda,
 * guardar variantes y grupos de opciones, y armar la respuesta con todo junto.
 */
@Service
public class ProductoDetalleService {

    private static final int MAX_VARIANTES = 8;
    private static final int MAX_GRUPOS = 8;
    private static final int MAX_OPCIONES_POR_GRUPO = 20;
    private static final int MAX_NOMBRE = 40;
    private static final int MAX_PERSONAS = 50;
    private static final BigDecimal PRECIO_MAXIMO = new BigDecimal("10000000");
    private static final BigDecimal ADICIONAL_MAXIMO = new BigDecimal("1000000");

    private final VarianteProductoRepository variantes;
    private final GrupoOpcionesRepository grupos;
    private final OpcionProductoRepository opciones;
    private final CatalogoTipos catalogo;

    public ProductoDetalleService(VarianteProductoRepository variantes,
                                  GrupoOpcionesRepository grupos,
                                  OpcionProductoRepository opciones,
                                  CatalogoTipos catalogo) {
        this.variantes = variantes;
        this.grupos = grupos;
        this.opciones = opciones;
        this.catalogo = catalogo;
    }

    public List<CatalogoTipos.TipoInfo> tipos() {
        return catalogo.catalogo();
    }

    public boolean tieneVariantes(UUID productoId) {
        return variantes.existsByProductoId(productoId);
    }

    // ==================== respuesta ====================

    public ProductoResponseDTO responder(Producto p, Oferta oferta) {
        if (p.getId() == null) {
            return ProductoResponseDTO.desde(p, oferta);
        }
        List<GrupoOpciones> gs = grupos.findByProductoIdOrderByOrdenAsc(p.getId());
        List<OpcionProducto> os = gs.isEmpty()
                ? List.of()
                : opciones.findByGrupoIdInOrderByOrdenAsc(gs.stream().map(GrupoOpciones::getId).toList());
        return ProductoResponseDTO.desde(p, oferta,
                variantes.findByProductoIdOrderByOrdenAsc(p.getId()),
                armarGrupos(gs, os));
    }

    /** Arma la lista de una tienda con pocas consultas extra, sin importar cuántos productos tenga. */
    public List<ProductoResponseDTO> responderTodos(List<Producto> productos, Map<UUID, Oferta> ofertas) {
        List<UUID> ids = productos.stream().map(Producto::getId).filter(Objects::nonNull).toList();

        Map<UUID, List<VarianteProducto>> variantesPorProducto = new HashMap<>();
        Map<UUID, List<GrupoOpcionesDTO>> gruposPorProducto = new HashMap<>();
        if (!ids.isEmpty()) {
            variantes.findByProductoIdInOrderByOrdenAsc(ids).forEach(v ->
                    variantesPorProducto.computeIfAbsent(v.getProductoId(), k -> new ArrayList<>()).add(v));

            List<GrupoOpciones> gs = grupos.findByProductoIdInOrderByOrdenAsc(ids);
            List<OpcionProducto> os = gs.isEmpty()
                    ? List.of()
                    : opciones.findByGrupoIdInOrderByOrdenAsc(gs.stream().map(GrupoOpciones::getId).toList());
            Map<UUID, List<GrupoOpciones>> gsPorProducto = new HashMap<>();
            gs.forEach(g -> gsPorProducto.computeIfAbsent(g.getProductoId(), k -> new ArrayList<>()).add(g));
            gsPorProducto.forEach((productoId, lista) -> gruposPorProducto.put(productoId, armarGrupos(lista, os)));
        }

        return productos.stream()
                .map(p -> ProductoResponseDTO.desde(
                        p,
                        p.getId() == null ? null : ofertas.get(p.getId()),
                        variantesPorProducto.getOrDefault(p.getId(), List.of()),
                        gruposPorProducto.getOrDefault(p.getId(), List.of())))
                .collect(Collectors.toList());
    }

    private static List<GrupoOpcionesDTO> armarGrupos(List<GrupoOpciones> gs, List<OpcionProducto> os) {
        Map<UUID, List<OpcionProducto>> porGrupo = new HashMap<>();
        os.forEach(o -> porGrupo.computeIfAbsent(o.getGrupoId(), k -> new ArrayList<>()).add(o));
        return gs.stream()
                .map(g -> GrupoOpcionesDTO.desde(g, porGrupo.getOrDefault(g.getId(), List.of())))
                .toList();
    }

    // ==================== validar y aplicar ====================

    /**
     * Valida el pedido de la tienda y copia los datos al producto (todavía sin guardar).
     * variantesSinCambios = edición hecha por la app antigua de un producto que ya tiene variantes:
     * se conservan su precio y su stock.
     */
    public void aplicar(Producto producto, ProductoRequestDTO req, boolean variantesSinCambios) {
        String nombre = req.nombre() == null ? "" : req.nombre().trim();
        if (nombre.length() < 2) {
            throw invalida("El nombre del producto es muy corto");
        }
        if (nombre.length() > 60) {
            throw invalida("El nombre del producto es muy largo (máximo 60 caracteres)");
        }

        String descripcion = req.descripcion() == null ? null : req.descripcion().trim();
        if (descripcion != null && descripcion.length() > 300) {
            throw invalida("La descripción es muy larga (máximo 300 caracteres)");
        }

        boolean mandaTipo = req.tipo() != null && !req.tipo().isBlank();
        TipoProducto tipo = mandaTipo ? parsearTipo(req.tipo()) : producto.getTipo();
        String atributos = producto.getAtributos();
        if (mandaTipo || req.atributos() != null) {
            atributos = catalogo.normalizar(tipo, req.atributos());
        }

        List<VarianteDTO> pedidas = req.variantes();
        if (pedidas != null) {
            validarVariantes(pedidas);
        }
        if (req.grupos() != null) {
            validarGrupos(req.grupos());
        }
        if (req.personas() != null && (req.personas() < 0 || req.personas() > MAX_PERSONAS)) {
            throw invalida("Las personas deben estar entre 1 y " + MAX_PERSONAS);
        }

        boolean conVariantes = pedidas != null && !pedidas.isEmpty();
        if (conVariantes) {
            producto.setPrecio(pedidas.stream().map(VarianteDTO::precio).min(Comparator.naturalOrder()).orElseThrow());
            producto.setStock(pedidas.stream().mapToInt(v -> v.stock() == null ? 0 : v.stock()).sum());
        } else if (!variantesSinCambios) {
            BigDecimal precio = req.precio();
            if (precio == null || precio.signum() <= 0 || precio.compareTo(PRECIO_MAXIMO) > 0) {
                throw invalida("Ingresá un precio válido, mayor a cero");
            }
            if (req.stock() != null && req.stock() < 0) {
                throw invalida("El stock no puede ser negativo");
            }
            producto.setPrecio(precio);
            if (req.stock() != null) {
                producto.setStock(req.stock());
            } else if (producto.getStock() == null) {
                producto.setStock(0);
            }
        }

        if (req.disponible() != null) {
            producto.setDisponible(req.disponible());
        }
        if (req.destacado() != null) {
            producto.setDestacado(req.destacado());
        }
        if (req.personas() != null) {
            producto.setPersonas(req.personas() == 0 ? null : req.personas());
        }
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setTipo(tipo);
        producto.setAtributos(atributos);
    }

    // ==================== guardar variantes y grupos ====================

    /** Se llama después de guardar el producto (necesita su id). */
    @Transactional
    public void guardarHijos(Producto producto, ProductoRequestDTO req) {
        if (req.variantes() != null) {
            sincronizarVariantes(producto, req.variantes());
        }
        if (req.grupos() != null) {
            sincronizarGrupos(producto, req.grupos());
        }
    }

    @Transactional
    public void eliminarDe(UUID productoId) {
        List<UUID> grupoIds = grupos.findByProductoIdOrderByOrdenAsc(productoId).stream()
                .map(GrupoOpciones::getId).toList();
        if (!grupoIds.isEmpty()) {
            opciones.deleteByGrupoIdIn(grupoIds);
        }
        grupos.deleteByProductoId(productoId);
        variantes.deleteByProductoId(productoId);
    }

    @Transactional
    public void eliminarDeProductos(Collection<UUID> productoIds) {
        if (productoIds.isEmpty()) {
            return;
        }
        List<UUID> grupoIds = grupos.findByProductoIdInOrderByOrdenAsc(productoIds).stream()
                .map(GrupoOpciones::getId).toList();
        if (!grupoIds.isEmpty()) {
            opciones.deleteByGrupoIdIn(grupoIds);
        }
        grupos.deleteByProductoIdIn(productoIds);
        variantes.deleteByProductoIdIn(productoIds);
    }

    private void sincronizarVariantes(Producto producto, List<VarianteDTO> pedidas) {
        List<VarianteProducto> actuales = variantes.findByProductoIdOrderByOrdenAsc(producto.getId());
        Map<UUID, VarianteProducto> porId = actuales.stream()
                .collect(Collectors.toMap(VarianteProducto::getId, v -> v));

        List<VarianteProducto> resultado = new ArrayList<>();
        int orden = 0;
        for (VarianteDTO dto : pedidas) {
            VarianteProducto v;
            if (dto.id() == null) {
                v = new VarianteProducto();
                v.setProductoId(producto.getId());
            } else {
                v = porId.get(dto.id());
                if (v == null) {
                    throw invalida("Una de las opciones ya no existe. Recargá el producto e intentá de nuevo");
                }
            }
            v.setNombre(dto.nombre().trim());
            v.setPrecio(dto.precio());
            v.setStock(dto.stock() == null ? 0 : dto.stock());
            v.setDisponible(dto.disponible() == null || dto.disponible());
            v.setOrden(orden++);
            resultado.add(v);
        }

        Set<UUID> conservadas = resultado.stream()
                .map(VarianteProducto::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<VarianteProducto> eliminadas = actuales.stream()
                .filter(v -> !conservadas.contains(v.getId())).toList();

        if (!eliminadas.isEmpty()) {
            variantes.deleteAll(eliminadas);
        }
        if (!resultado.isEmpty()) {
            variantes.saveAll(resultado);
        }
    }

    private void sincronizarGrupos(Producto producto, List<GrupoOpcionesDTO> pedidos) {
        List<GrupoOpciones> actuales = grupos.findByProductoIdOrderByOrdenAsc(producto.getId());
        Map<UUID, GrupoOpciones> gruposPorId = actuales.stream()
                .collect(Collectors.toMap(GrupoOpciones::getId, g -> g));
        List<UUID> idsActuales = actuales.stream().map(GrupoOpciones::getId).toList();
        Map<UUID, OpcionProducto> opcionesPorId = idsActuales.isEmpty()
                ? Map.of()
                : opciones.findByGrupoIdInOrderByOrdenAsc(idsActuales).stream()
                        .collect(Collectors.toMap(OpcionProducto::getId, o -> o));

        Set<UUID> gruposConservados = new HashSet<>();
        Set<UUID> opcionesConservadas = new HashSet<>();
        List<OpcionProducto> paraGuardar = new ArrayList<>();

        int ordenGrupo = 0;
        for (GrupoOpcionesDTO dto : pedidos) {
            GrupoOpciones g;
            if (dto.id() == null) {
                g = new GrupoOpciones();
                g.setProductoId(producto.getId());
            } else {
                g = gruposPorId.get(dto.id());
                if (g == null) {
                    throw invalida("Uno de los grupos de opciones ya no existe. Recargá el producto e intentá de nuevo");
                }
                gruposConservados.add(g.getId());
            }

            int minimo = minimoDe(dto);
            g.setNombre(dto.nombre().trim());
            g.setMinimo(minimo);
            g.setMaximo(maximoDe(dto, minimo));
            g.setOrden(ordenGrupo++);
            grupos.save(g);

            int ordenOpcion = 0;
            for (OpcionDTO od : dto.opciones()) {
                OpcionProducto o;
                if (od.id() == null) {
                    o = new OpcionProducto();
                } else {
                    o = opcionesPorId.get(od.id());
                    if (o == null || !Objects.equals(o.getGrupoId(), g.getId())) {
                        throw invalida("Una de las opciones ya no existe. Recargá el producto e intentá de nuevo");
                    }
                    opcionesConservadas.add(o.getId());
                }
                o.setGrupoId(g.getId());
                o.setNombre(od.nombre().trim());
                o.setPrecioAdicional(od.precioAdicional() == null ? BigDecimal.ZERO : od.precioAdicional());
                o.setDisponible(od.disponible() == null || od.disponible());
                o.setOrden(ordenOpcion++);
                paraGuardar.add(o);
            }
        }

        // Las opciones de un grupo eliminado también quedan acá, porque no están entre las conservadas
        List<OpcionProducto> opcionesEliminadas = opcionesPorId.values().stream()
                .filter(o -> !opcionesConservadas.contains(o.getId())).toList();
        List<GrupoOpciones> gruposEliminados = actuales.stream()
                .filter(g -> !gruposConservados.contains(g.getId())).toList();

        if (!opcionesEliminadas.isEmpty()) {
            opciones.deleteAll(opcionesEliminadas);
        }
        if (!gruposEliminados.isEmpty()) {
            grupos.deleteAll(gruposEliminados);
        }
        if (!paraGuardar.isEmpty()) {
            opciones.saveAll(paraGuardar);
        }
    }

    // ==================== validaciones ====================

    private void validarVariantes(List<VarianteDTO> pedidas) {
        if (pedidas.size() > MAX_VARIANTES) {
            throw invalida("Un producto puede tener hasta " + MAX_VARIANTES + " opciones de tamaño");
        }
        Set<String> nombres = new HashSet<>();
        for (VarianteDTO v : pedidas) {
            if (v == null) {
                throw invalida("Hay una opción vacía");
            }
            String nombre = v.nombre() == null ? "" : v.nombre().trim();
            if (nombre.isEmpty()) {
                throw invalida("Cada opción necesita un nombre");
            }
            if (nombre.length() > MAX_NOMBRE) {
                throw invalida("El nombre \"" + nombre + "\" es muy largo (máximo " + MAX_NOMBRE + " caracteres)");
            }
            if (!nombres.add(nombre.toLowerCase(Locale.ROOT))) {
                throw invalida("Hay opciones repetidas: " + nombre);
            }
            if (v.precio() == null || v.precio().signum() <= 0 || v.precio().compareTo(PRECIO_MAXIMO) > 0) {
                throw invalida("La opción \"" + nombre + "\" necesita un precio válido, mayor a cero");
            }
            if (v.stock() != null && v.stock() < 0) {
                throw invalida("El stock de \"" + nombre + "\" no puede ser negativo");
            }
        }
    }

    private void validarGrupos(List<GrupoOpcionesDTO> pedidos) {
        if (pedidos.size() > MAX_GRUPOS) {
            throw invalida("Un producto puede tener hasta " + MAX_GRUPOS + " grupos de opciones");
        }
        Set<String> nombresGrupo = new HashSet<>();
        for (GrupoOpcionesDTO g : pedidos) {
            if (g == null) {
                throw invalida("Hay un grupo de opciones vacío");
            }
            String nombre = g.nombre() == null ? "" : g.nombre().trim();
            if (nombre.length() < 2) {
                throw invalida("Cada grupo de opciones necesita un nombre");
            }
            if (nombre.length() > MAX_NOMBRE) {
                throw invalida("El grupo \"" + nombre + "\" tiene un nombre muy largo (máximo " + MAX_NOMBRE + " caracteres)");
            }
            if (!nombresGrupo.add(nombre.toLowerCase(Locale.ROOT))) {
                throw invalida("Hay grupos repetidos: " + nombre);
            }

            List<OpcionDTO> lista = g.opciones();
            if (lista == null || lista.isEmpty()) {
                throw invalida("El grupo \"" + nombre + "\" necesita al menos una opción");
            }
            if (lista.size() > MAX_OPCIONES_POR_GRUPO) {
                throw invalida("El grupo \"" + nombre + "\" admite hasta " + MAX_OPCIONES_POR_GRUPO + " opciones");
            }

            int minimo = g.minimo() == null ? 0 : g.minimo();
            if (minimo < 0) {
                throw invalida("El mínimo del grupo \"" + nombre + "\" no puede ser negativo");
            }
            if (g.maximo() != null && g.maximo() < 1) {
                throw invalida("El máximo del grupo \"" + nombre + "\" debe ser al menos 1");
            }
            if (g.maximo() != null && g.maximo() < minimo) {
                throw invalida("El máximo del grupo \"" + nombre + "\" no puede ser menor que su mínimo");
            }
            if (minimo > lista.size()) {
                throw invalida("El grupo \"" + nombre + "\" pide elegir " + minimo + " pero solo tiene " + lista.size() + " opciones");
            }

            Set<String> nombresOpcion = new HashSet<>();
            for (OpcionDTO o : lista) {
                if (o == null) {
                    throw invalida("Hay una opción vacía en \"" + nombre + "\"");
                }
                String nombreOpcion = o.nombre() == null ? "" : o.nombre().trim();
                if (nombreOpcion.isEmpty()) {
                    throw invalida("Cada opción de \"" + nombre + "\" necesita un nombre");
                }
                if (nombreOpcion.length() > MAX_NOMBRE) {
                    throw invalida("La opción \"" + nombreOpcion + "\" tiene un nombre muy largo (máximo " + MAX_NOMBRE + " caracteres)");
                }
                if (!nombresOpcion.add(nombreOpcion.toLowerCase(Locale.ROOT))) {
                    throw invalida("Hay opciones repetidas en \"" + nombre + "\": " + nombreOpcion);
                }
                if (o.precioAdicional() != null
                        && (o.precioAdicional().signum() < 0 || o.precioAdicional().compareTo(ADICIONAL_MAXIMO) > 0)) {
                    throw invalida("La opción \"" + nombreOpcion + "\" tiene un precio adicional inválido");
                }
            }
        }
    }

    private static int minimoDe(GrupoOpcionesDTO dto) {
        return dto.minimo() == null ? 0 : dto.minimo();
    }

    /** Sin máximo se asume 1 (o el mínimo, si es mayor); nunca más que la cantidad de opciones. */
    private static int maximoDe(GrupoOpcionesDTO dto, int minimo) {
        int maximo = dto.maximo() == null ? Math.max(1, minimo) : dto.maximo();
        return Math.min(maximo, dto.opciones().size());
    }

    private static TipoProducto parsearTipo(String texto) {
        try {
            return TipoProducto.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalida("Tipo de producto desconocido: " + texto);
        }
    }

    private static ResponseStatusException invalida(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}