package com.aulahub.backend.repository;

import com.aulahub.backend.model.ActividadLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActividadLogRepository extends JpaRepository<ActividadLogEntity, Long> {

    // Filtros opcionales compartidos por las tres consultas: accion exacta, entidad exacta,
    // texto libre (usuario/accion/detalle) y rango de fechas. Cada filtro se ignora cuando
    // su parametro llega null, gracias al patron ":x IS NULL OR ...".

    // ADMIN — logs paginados de acciones sobre reservas de sus aulas asignadas
    @Query(value = "SELECT a FROM ActividadLogEntity a WHERE " +
                   "a.entidad = 'reserva' AND " +
                   "a.entidadId IN (SELECT r.id FROM ReservaEntity r WHERE r.aula.id IN :aulaIds) AND " +
                   "(:accion IS NULL OR a.accion = :accion) AND " +
                   "(:entidad IS NULL OR a.entidad = :entidad) AND " +
                   "(:q IS NULL OR LOWER(a.usuario.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.accion) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.detalle) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
                   "(:desde IS NULL OR a.createdAt >= :desde) AND " +
                   "(:hasta IS NULL OR a.createdAt <= :hasta)",
           countQuery = "SELECT COUNT(a) FROM ActividadLogEntity a WHERE " +
                   "a.entidad = 'reserva' AND " +
                   "a.entidadId IN (SELECT r.id FROM ReservaEntity r WHERE r.aula.id IN :aulaIds) AND " +
                   "(:accion IS NULL OR a.accion = :accion) AND " +
                   "(:entidad IS NULL OR a.entidad = :entidad) AND " +
                   "(:q IS NULL OR LOWER(a.usuario.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.accion) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.detalle) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
                   "(:desde IS NULL OR a.createdAt >= :desde) AND " +
                   "(:hasta IS NULL OR a.createdAt <= :hasta)")
    Page<ActividadLogEntity> findByAulaIdsFiltrado(
            @Param("aulaIds") List<Long> aulaIds,
            @Param("q") String q,
            @Param("accion") String accion,
            @Param("entidad") String entidad,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable);

    // SUPER_ADMIN — logs paginados de usuarios con algun rol en su facultad
    @Query(value = "SELECT a FROM ActividadLogEntity a WHERE " +
                   "a.usuario.id IN (SELECT r.usuario.id FROM RolEntity r WHERE r.facultad.id = :facultadId) AND " +
                   "(:accion IS NULL OR a.accion = :accion) AND " +
                   "(:entidad IS NULL OR a.entidad = :entidad) AND " +
                   "(:q IS NULL OR LOWER(a.usuario.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.accion) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.detalle) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
                   "(:desde IS NULL OR a.createdAt >= :desde) AND " +
                   "(:hasta IS NULL OR a.createdAt <= :hasta)",
           countQuery = "SELECT COUNT(a) FROM ActividadLogEntity a WHERE " +
                   "a.usuario.id IN (SELECT r.usuario.id FROM RolEntity r WHERE r.facultad.id = :facultadId) AND " +
                   "(:accion IS NULL OR a.accion = :accion) AND " +
                   "(:entidad IS NULL OR a.entidad = :entidad) AND " +
                   "(:q IS NULL OR LOWER(a.usuario.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.accion) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.detalle) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
                   "(:desde IS NULL OR a.createdAt >= :desde) AND " +
                   "(:hasta IS NULL OR a.createdAt <= :hasta)")
    Page<ActividadLogEntity> findByFacultadIdFiltrado(
            @Param("facultadId") Long facultadId,
            @Param("q") String q,
            @Param("accion") String accion,
            @Param("entidad") String entidad,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable);

    // ADMIN_CENTRAL — toda la actividad del sistema, solo acotada por los filtros opcionales
    @Query(value = "SELECT a FROM ActividadLogEntity a WHERE " +
                   "(:accion IS NULL OR a.accion = :accion) AND " +
                   "(:entidad IS NULL OR a.entidad = :entidad) AND " +
                   "(:q IS NULL OR LOWER(a.usuario.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.accion) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.detalle) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
                   "(:desde IS NULL OR a.createdAt >= :desde) AND " +
                   "(:hasta IS NULL OR a.createdAt <= :hasta)",
           countQuery = "SELECT COUNT(a) FROM ActividadLogEntity a WHERE " +
                   "(:accion IS NULL OR a.accion = :accion) AND " +
                   "(:entidad IS NULL OR a.entidad = :entidad) AND " +
                   "(:q IS NULL OR LOWER(a.usuario.nombre) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.accion) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "    OR LOWER(a.detalle) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
                   "(:desde IS NULL OR a.createdAt >= :desde) AND " +
                   "(:hasta IS NULL OR a.createdAt <= :hasta)")
    Page<ActividadLogEntity> findFiltrado(
            @Param("q") String q,
            @Param("accion") String accion,
            @Param("entidad") String entidad,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable);
}
