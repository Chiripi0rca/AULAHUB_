package com.aulahub.backend.repository;

import com.aulahub.backend.model.FacultadEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacultadRepository extends JpaRepository<FacultadEntity, Long> {

    // Verifica si ya existe una facultad con esa clave — para validar duplicados al crear
    boolean existsByClave(String clave);

    // Verifica duplicado de clave excluyendo la propia facultad — para validar al actualizar
    boolean existsByClaveAndIdNot(String clave, Long id);

    // Contador de facultades activas — para DashboardService (ADMIN_CENTRAL)
    long countByActivaTrue();
}
