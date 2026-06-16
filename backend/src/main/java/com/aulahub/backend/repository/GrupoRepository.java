package com.aulahub.backend.repository;

import com.aulahub.backend.model.GrupoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrupoRepository extends JpaRepository<GrupoEntity, Long> {

    // Grupos activos de una facultad — para dropdowns y asignación
    List<GrupoEntity> findByFacultad_IdAndActivoTrue(Long facultadId);

    // Unicidad del nombre del grupo dentro de la facultad
    boolean existsByFacultad_IdAndNombre(Long facultadId, String nombre);
    boolean existsByFacultad_IdAndNombreAndIdNot(Long facultadId, String nombre, Long id);
}
