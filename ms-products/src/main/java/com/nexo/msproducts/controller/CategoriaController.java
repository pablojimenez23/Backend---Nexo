package com.nexo.msproducts.controller;

import com.nexo.msproducts.dto.CategoriaRequestDTO;
import com.nexo.msproducts.dto.CategoriaResponseDTO;
import com.nexo.msproducts.entity.Categoria;
import com.nexo.msproducts.repository.CategoriaRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/categorias")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;

    public CategoriaController(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    // Público — el catálogo de categorías se usa para filtrar productos sin necesitar cuenta
    @GetMapping
    public List<CategoriaResponseDTO> listar() {
        return categoriaRepository.findAll()
                .stream().map(CategoriaResponseDTO::desde).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> crear(@Valid @RequestBody CategoriaRequestDTO request) {
        Categoria categoria = new Categoria();
        categoria.setNombre(request.nombre());
        Categoria guardada = categoriaRepository.save(categoria);
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoriaResponseDTO.desde(guardada));
    }
}