package com.aulahub.backend.repository;

import com.aulahub.backend.model.TipoReservaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TipoReservaRepository extends JpaRepository<TipoReservaEntity, Long> {

    // Tipos de reserva activos de una facultad — usado para alimentar el dropdown de reserva
    List<TipoReservaEntity> findByFacultad_IdAndActivoTrue(Long facultadId);

    // Unicidad de la clave dentro de la facultad (clave + facultad es única — ver V1)
    boolean existsByFacultad_IdAndClave(Long facultadId, String clave);
    boolean existsByFacultad_IdAndClaveAndIdNot(Long facultadId, String clave, Long id);

    // Desactiva todos los tipos de reserva de una facultad en un solo UPDATE — usado en FacultadService.desactivar()
    @Modifying
    @Query("UPDATE TipoReservaEntity t SET t.activo = false WHERE t.facultad.id = :facultadId")
    void desactivarByFacultadId(@Param("facultadId") Long facultadId);
}
