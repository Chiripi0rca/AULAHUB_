package com.aulahub.backend.repository;

import com.aulahub.backend.model.AsignacionMaestroEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AsignacionMaestroRepository extends JpaRepository<AsignacionMaestroEntity, Long> {

    // Todas las asignaciones (grupo, materia) de un maestro
    List<AsignacionMaestroEntity> findByUsuario_Id(Long usuarioId);

    // Borra todas las asignaciones de un maestro en un solo DELETE (se ejecuta de inmediato).
    // Evita el conflicto con la restricción única al reemplazar el set completo.
    @Modifying
    @Query("DELETE FROM AsignacionMaestroEntity a WHERE a.usuario.id = :usuarioId")
    void deleteByUsuario_Id(@Param("usuarioId") Long usuarioId);
}
