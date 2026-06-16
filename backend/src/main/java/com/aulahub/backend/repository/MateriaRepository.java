package com.aulahub.backend.repository;

import com.aulahub.backend.model.MateriaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MateriaRepository extends JpaRepository<MateriaEntity, Long> {

    // Materias activas de una facultad — usado para alimentar dropdowns por facultad
    List<MateriaEntity> findByFacultad_IdAndActivaTrue(Long facultadId);

    // Desactiva todas las materias de una facultad en un solo UPDATE — usado en FacultadService.desactivar()
    @Modifying
    @Query("UPDATE MateriaEntity m SET m.activa = false WHERE m.facultad.id = :facultadId")
    void desactivarByFacultadId(@Param("facultadId") Long facultadId);
}
