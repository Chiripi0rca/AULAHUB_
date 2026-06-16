package com.aulahub.backend.repository;

import com.aulahub.backend.model.AulaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AulaRepository extends JpaRepository<AulaEntity, Long> {

    // Aulas activas de una facultad — usado en AulaService.listarPorFacultad()
    List<AulaEntity> findByFacultadIdAndActivaTrue(Long facultadId);

    // Todas las aulas activas del sistema — usado en AulaService.listar()
    List<AulaEntity> findByActivaTrue();

    // Unicidad de código dentro de la facultad — usado en AulaService.crear()
    boolean existsByFacultadIdAndCodigo(Long facultadId, String codigo);

    // Unicidad de código excluyendo la propia aula — usado en AulaService.actualizar()
    boolean existsByFacultadIdAndCodigoAndIdNot(Long facultadId, String codigo, Long id);

    // Contadores para DashboardService
    long countByFacultadId(Long facultadId);
    long countByFacultadIdAndActivaTrue(Long facultadId);
    long countByActivaTrue();

    // Desactiva todas las aulas de una facultad en un solo UPDATE — usado en FacultadService.desactivar()
    @Modifying
    @Query("UPDATE AulaEntity a SET a.activa = false WHERE a.facultad.id = :facultadId")
    void desactivarByFacultadId(@Param("facultadId") Long facultadId);
}
