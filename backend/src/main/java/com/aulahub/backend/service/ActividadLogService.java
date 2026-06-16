package com.aulahub.backend.service;

import com.aulahub.backend.dto.response.ActividadLogResponseDTO;
import com.aulahub.backend.model.ActividadLogEntity;
import com.aulahub.backend.model.UserEntity;
import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.repository.ActividadLogRepository;
import com.aulahub.backend.repository.RolRepository;
import com.aulahub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActividadLogService {

    private final ActividadLogRepository actividadLogRepository;
    private final UserRepository userRepository;
    private final RolRepository rolRepository;

    // ADMIN — logs paginados filtrados por las aulas que tiene asignadas en su turno
    @Transactional(readOnly = true)
    public Page<ActividadLogResponseDTO> listarParaAdmin(Long usuarioId, String q, String accion,
                                                         String entidad, LocalDate desde, LocalDate hasta,
                                                         Pageable pageable) {
        List<Long> aulaIds = rolRepository.findByUsuarioId(usuarioId).stream()
                .filter(r -> r.getTipo() == TipoRol.ADMIN)
                .flatMap(r -> r.getAulas().stream().map(a -> a.getId()))
                .distinct()
                .toList();

        if (aulaIds.isEmpty()) return Page.empty(pageable);

        return actividadLogRepository.findByAulaIdsFiltrado(
                aulaIds, limpiar(q), limpiar(accion), limpiar(entidad),
                inicioDelDia(desde), finDelDia(hasta), pageable).map(this::toDTO);
    }

    // SUPER_ADMIN — logs paginados de toda la actividad dentro de su facultad
    @Transactional(readOnly = true)
    public Page<ActividadLogResponseDTO> listarParaSuperAdmin(Long usuarioId, String q, String accion,
                                                              String entidad, LocalDate desde, LocalDate hasta,
                                                              Pageable pageable) {
        Long facultadId = rolRepository.findByUsuarioId(usuarioId).stream()
                .filter(r -> r.getTipo() == TipoRol.SUPER_ADMIN && r.getFacultad() != null)
                .map(r -> r.getFacultad().getId())
                .findFirst()
                .orElse(null);

        if (facultadId == null) return Page.empty(pageable);

        return actividadLogRepository.findByFacultadIdFiltrado(
                facultadId, limpiar(q), limpiar(accion), limpiar(entidad),
                inicioDelDia(desde), finDelDia(hasta), pageable).map(this::toDTO);
    }

    // ADMIN_CENTRAL — logs paginados de toda la actividad del sistema, solo con los filtros opcionales
    @Transactional(readOnly = true)
    public Page<ActividadLogResponseDTO> listarParaAdminCentral(String q, String accion, String entidad,
                                                                LocalDate desde, LocalDate hasta,
                                                                Pageable pageable) {
        return actividadLogRepository.findFiltrado(
                limpiar(q), limpiar(accion), limpiar(entidad),
                inicioDelDia(desde), finDelDia(hasta), pageable).map(this::toDTO);
    }

    // Registra una nueva entrada en el log — llamado internamente desde otros services
    // Usa getReferenceById para no cargar la entidad completa, solo necesitamos el FK
    @Transactional
    public void registrarAccionActual(String accion, String entidad, Long entidadId, String detalle) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        userRepository.findByEmail(email).ifPresent(usuario ->
                actividadLogRepository.save(
                        ActividadLogEntity.builder()
                                .usuario(usuario)
                                .accion(accion)
                                .entidad(entidad)
                                .entidadId(entidadId)
                                .detalle(detalle)
                                .build()
                )
        );
    }

    // Registra una entrada atribuida a un usuario explícito (no usa SecurityContext).
    // Necesario para procesos automáticos como el scheduler, donde no hay usuario autenticado.
    @Transactional
    public void registrarParaUsuario(UserEntity usuario, String accion, String entidad, Long entidadId, String detalle) {
        if (usuario == null) return;
        actividadLogRepository.save(
                ActividadLogEntity.builder()
                        .usuario(usuario)
                        .accion(accion)
                        .entidad(entidad)
                        .entidadId(entidadId)
                        .detalle(detalle)
                        .build()
        );
    }

    // ── helpers de filtros ─────────────────────────────────────────────────────

    // Texto vacio o solo espacios se trata como "sin filtro" (null).
    private static String limpiar(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    // Convierte la fecha "desde" al inicio del dia (00:00) para comparar contra createdAt.
    private static LocalDateTime inicioDelDia(LocalDate d) {
        return d == null ? null : d.atStartOfDay();
    }

    // Convierte la fecha "hasta" al final del dia (23:59:59.999...) para incluir todo ese dia.
    private static LocalDateTime finDelDia(LocalDate d) {
        return d == null ? null : d.atTime(LocalTime.MAX);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private ActividadLogResponseDTO toDTO(ActividadLogEntity log) {
        return new ActividadLogResponseDTO(
                log.getId(),
                log.getUsuario().getId(),
                log.getUsuario().getNombre(),
                log.getAccion(),
                log.getEntidad(),
                log.getEntidadId(),
                log.getDetalle(),
                log.getCreatedAt()
        );
    }
}
