package com.aulahub.backend.repository;

import com.aulahub.backend.model.RolEntity;
import com.aulahub.backend.model.enums.TipoRol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolRepository extends JpaRepository<RolEntity, Long> { // Interfaz de repositorio para la entidad Rol, para usar métodos CRUD básicos

    // Carga todos los roles de un usuario — usado en UserDetailServiceImpl para construir sus authorities
    List<RolEntity> findByUsuarioId(Long usuarioId);

    // Todos los roles de un tipo concreto — usado para listar maestros (tipo PROFESOR)
    List<RolEntity> findByTipo(TipoRol tipo);

    // Todos los roles de una facultad — usado en gestionar-roles para mostrar usuarios por facultad
    List<RolEntity> findByFacultadId(Long facultadId);

    // Elimina el rol de un usuario en una facultad específica — reemplaza sin tocar otras facultades
    void deleteByUsuarioIdAndFacultadId(Long usuarioId, Long facultadId);

    // Usuarios activos distintos que tienen algún rol en la facultad dada — para DashboardService (SUPER_ADMIN)
    @Query("SELECT COUNT(DISTINCT r.usuario.id) FROM RolEntity r WHERE r.facultad.id = :facultadId AND r.usuario.activo = true")
    long countUsuariosActivosByFacultadId(@Param("facultadId") Long facultadId);

    // Roles de un tipo que tienen asignada un aula concreta — para avisar a los admins de esa aula
    @Query("SELECT r FROM RolEntity r JOIN r.aulas a WHERE a.id = :aulaId AND r.tipo = :tipo")
    List<RolEntity> findByAulaIdAndTipo(@Param("aulaId") Long aulaId, @Param("tipo") TipoRol tipo);
}
