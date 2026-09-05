package com.nexo.msstores.controller;

import com.nexo.msstores.dto.CalificacionResponseDTO;
import com.nexo.msstores.repository.CalificacionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/calificaciones-tiendas")
public class AdminCalificacionController {

    private final CalificacionRepository calificacionRepository;

    public AdminCalificacionController(CalificacionRepository calificacionRepository) {
        this.calificacionRepository = calificacionRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<CalificacionResponseDTO> listarTodas() {
        return calificacionRepository.findAll()
                .stream().map(CalificacionResponseDTO::desde).collect(Collectors.toList());
    }
}