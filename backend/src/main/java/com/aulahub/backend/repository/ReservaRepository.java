package com.aulahub.backend.repository;

import com.aulahub.backend.model.ReservaEntity;
import com.aulahub.backend.model.enums.EstadoReserva;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ReservaRepository extends JpaRepository<ReservaEntity, Long> {

    // Lista paginada de reservas de un usuario específico
    Page<ReservaEntity> findBySolicitante_Id(Long usuarioId, Pageable pageable);

    // Lista paginada de reservas de un aula específica
    Page<ReservaEntity> findByAula_Id(Long aulaId, Pageable pageable);

    // Reservas de un aula dentro de un rango de fechas y con ciertos estados
    // Usado para pintar la cuadrícula semanal del calendario (slots ocupados)
    List<ReservaEntity> findByAula_IdAndFechaBetweenAndStatusInOrderByFechaAscHoraInicioAsc(
            Long aulaId, LocalDate desde, LocalDate hasta, List<EstadoReserva> estados);

    // Lista reservas PENDIENTES cuya fecha ya pasó — usado por el scheduler de medianoche
    List<ReservaEntity> findByStatusAndFechaBefore(EstadoReserva status, LocalDate fecha);

    // Detecta si existe una reserva activa (PENDIENTE o ACEPTADA) que solape
    // el mismo aula, la misma fecha y el mismo rango de horas
    // Usado en ReservaService.crear() antes de guardar para evitar conflictos
    @Query("SELECT COUNT(r) > 0 FROM ReservaEntity r WHERE " +
           "r.aula.id = :aulaId AND " +
           "r.fecha = :fecha AND " +
           "r.horaInicio < :horaFin AND " +
           "r.horaFin > :horaInicio AND " +
           "r.status IN :estados")
    boolean existeConflicto(@Param("aulaId") Long aulaId,
                            @Param("fecha") LocalDate fecha,
                            @Param("horaInicio") LocalTime horaInicio,
                            @Param("horaFin") LocalTime horaFin,
                            @Param("estados") List<EstadoReserva> estados);

    // Reservas que solapan el mismo aula/fecha/horario en un estado dado, excluyendo una reserva.
    // Usado al aceptar una solicitud para rechazar automáticamente las demás que competían por el slot.
    @Query("SELECT r FROM ReservaEntity r WHERE " +
           "r.aula.id = :aulaId AND " +
           "r.fecha = :fecha AND " +
           "r.horaInicio < :horaFin AND " +
           "r.horaFin > :horaInicio AND " +
           "r.status = :estado AND " +
           "r.id <> :excluirId")
    List<ReservaEntity> findSolapadasPorEstado(@Param("aulaId") Long aulaId,
                                               @Param("fecha") LocalDate fecha,
                                               @Param("horaInicio") LocalTime horaInicio,
                                               @Param("horaFin") LocalTime horaFin,
                                               @Param("estado") EstadoReserva estado,
                                               @Param("excluirId") Long excluirId);

    // ── Queries para DashboardService ────────────────────────────────────────────

    // Cuenta reservas de una lista de aulas con un estado concreto (ADMIN — sus aulas asignadas)
    @Query("SELECT COUNT(r) FROM ReservaEntity r WHERE r.aula.id IN :aulaIds AND r.status = :status")
    long countByAulasAndStatus(@Param("aulaIds") List<Long> aulaIds, @Param("status") EstadoReserva status);

    // Cuenta reservas de una lista de aulas para una fecha con ciertos estados (hoy — ADMIN)
    @Query("SELECT COUNT(r) FROM ReservaEntity r WHERE r.aula.id IN :aulaIds AND r.fecha = :fecha AND r.status IN :estados")
    long countByAulasAndFechaAndEstados(@Param("aulaIds") List<Long> aulaIds,
                                        @Param("fecha") LocalDate fecha,
                                        @Param("estados") List<EstadoReserva> estados);

    // Cuenta reservas de toda una facultad con un estado concreto (SUPER_ADMIN)
    @Query("SELECT COUNT(r) FROM ReservaEntity r WHERE r.aula.facultad.id = :facultadId AND r.status = :status")
    long countByFacultadAndStatus(@Param("facultadId") Long facultadId, @Param("status") EstadoReserva status);

    // Cuenta reservas de toda una facultad para una fecha con ciertos estados (hoy — SUPER_ADMIN)
    @Query("SELECT COUNT(r) FROM ReservaEntity r WHERE r.aula.facultad.id = :facultadId AND r.fecha = :fecha AND r.status IN :estados")
    long countByFacultadAndFechaAndEstados(@Param("facultadId") Long facultadId,
                                           @Param("fecha") LocalDate fecha,
                                           @Param("estados") List<EstadoReserva> estados);

    // Cuenta reservas globales por estado (ADMIN_CENTRAL)
    long countByStatus(EstadoReserva status);

    // Cuenta reservas globales para una fecha con ciertos estados (hoy — ADMIN_CENTRAL)
    @Query("SELECT COUNT(r) FROM ReservaEntity r WHERE r.fecha = :fecha AND r.status IN :estados")
    long countByFechaAndEstados(@Param("fecha") LocalDate fecha,
                                @Param("estados") List<EstadoReserva> estados);

    // Lista todas las reservas de un aula sin paginación — para reportes PDF (sin filtro de fecha)
    List<ReservaEntity> findByAula_IdOrderByFechaAscHoraInicioAsc(Long aulaId);

    // Lista reservas de un aula en un rango de fechas — para reportes PDF con filtro
    List<ReservaEntity> findByAula_IdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
            Long aulaId, LocalDate desde, LocalDate hasta);

    // Lista todas las reservas de un solicitante sin paginación — para reportes PDF (sin filtro)
    List<ReservaEntity> findBySolicitante_IdOrderByFechaAscHoraInicioAsc(Long usuarioId);

    // Lista reservas de un solicitante en un rango de fechas — para reportes PDF con filtro
    List<ReservaEntity> findBySolicitante_IdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
            Long usuarioId, LocalDate desde, LocalDate hasta);

    // Lista reservas en un rango de fechas — para Excel con filtro
    List<ReservaEntity> findByFechaBetweenOrderByFechaAscHoraInicioAsc(LocalDate desde, LocalDate hasta);

    // Reservas de una facultad paginadas — para RLS de SUPER_ADMIN en listar()
    @Query("SELECT r FROM ReservaEntity r WHERE r.aula.facultad.id = :facultadId")
    Page<ReservaEntity> findByFacultadId(@Param("facultadId") Long facultadId, Pageable pageable);
}
