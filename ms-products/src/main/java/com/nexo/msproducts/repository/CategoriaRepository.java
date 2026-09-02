package com.nexo.msproducts.repository;

import com.nexo.msproducts.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {
}