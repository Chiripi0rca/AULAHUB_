package com.aulahub.backend.service;

import com.aulahub.backend.dto.response.AdminCentralDashboardResponseDTO;
import com.aulahub.backend.dto.response.AdminDashboardResponseDTO;
import com.aulahub.backend.dto.response.AdminSuperDashboardResponseDTO;
import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.exception.UsuarioNoEncontradoException;
import com.aulahub.backend.model.RolEntity;
import com.aulahub.backend.model.enums.EstadoReserva;
import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.repository.AulaRepository;
import com.aulahub.backend.repository.FacultadRepository;
import com.aulahub.backend.repository.ReservaRepository;
import com.aulahub.backend.repository.RolRepository;
import com.aulahub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ReservaRepository reservaRepository;
    private final UserRepository userRepository;
    private final AulaRepository aulaRepository;
    private final FacultadRepository facultadRepository;
    private final RolRepository rolRepository;

    private static final List<EstadoReserva> ACTIVOS = List.of(
            EstadoReserva.PENDIENTE, EstadoReserva.ACEPTADA);

    // Panel ADMIN — estadísticas filtradas por sus aulas asignadas y su turno
    @Transactional(readOnly = true)
    public AdminDashboardResponseDTO obtenerPanelAdmin(Long usuarioId) {
        validarIdentidad(usuarioId);
        RolEntity rol = rolRepository.findByUsuarioId(usuarioId).stream()
                .filter(r -> r.getTipo() == TipoRol.ADMIN)
                .findFirst()
                .orElseThrow(() -> new OperacionNoPermitidaException(
                        "El usuario " + usuarioId + " no tiene rol ADMIN"));

        List<Long> aulaIds = rol.getAulas().stream()
                .map(a -> a.getId())
                .toList();

        String nombreFacultad = rol.getFacultad() != null ? rol.getFacultad().getNombre() : "";
        String turno = rol.getTurno() != null ? rol.getTurno().name() : "";

        if (aulaIds.isEmpty()) {
            return new AdminDashboardResponseDTO(nombreFacultad, turno, 0, 0, 0, 0);
        }

        int pendientes = (int) reservaRepository.countByAulasAndStatus(aulaIds, EstadoReserva.PENDIENTE);
        int aceptadas  = (int) reservaRepository.countByAulasAndStatus(aulaIds, EstadoReserva.ACEPTADA);
        int hoy        = (int) reservaRepository.countByAulasAndFechaAndEstados(aulaIds, LocalDate.now(), ACTIVOS);

        return new AdminDashboardResponseDTO(
                nombreFacultad,
                turno,
                aulaIds.size(),
                pendientes,
                aceptadas,
                hoy
        );
    }

    // Panel SUPER_ADMIN — estadísticas completas de su facultad (todos los turnos)
    @Transactional(readOnly = true)
    public AdminSuperDashboardResponseDTO obtenerPanelSuperAdmin(Long usuarioId) {
        validarIdentidad(usuarioId);
        RolEntity rol = rolRepository.findByUsuarioId(usuarioId).stream()
                .filter(r -> r.getTipo() == TipoRol.SUPER_ADMIN)
                .findFirst()
                .orElseThrow(() -> new OperacionNoPermitidaException(
                        "El usuario " + usuarioId + " no tiene rol SUPER_ADMIN"));

        Long facultadId = rol.getFacultad().getId();
        String nombreFacultad = rol.getFacultad().getNombre();

        int totalAulas        = (int) aulaRepository.countByFacultadId(facultadId);
        int totalAulasActivas = (int) aulaRepository.countByFacultadIdAndActivaTrue(facultadId);
        int pendientes        = (int) reservaRepository.countByFacultadAndStatus(facultadId, EstadoReserva.PENDIENTE);
        int aceptadas         = (int) reservaRepository.countByFacultadAndStatus(facultadId, EstadoReserva.ACEPTADA);
        int hoy               = (int) reservaRepository.countByFacultadAndFechaAndEstados(facultadId, LocalDate.now(), ACTIVOS);
        int usuariosActivos   = (int) rolRepository.countUsuariosActivosByFacultadId(facultadId);

        return new AdminSuperDashboardResponseDTO(
                nombreFacultad,
                totalAulas,
                totalAulasActivas,
                pendientes,
                aceptadas,
                hoy,
                usuariosActivos
        );
    }

    // Panel ADMIN_CENTRAL — estadísticas globales de todas las facultades
    @Transactional(readOnly = true)
    public AdminCentralDashboardResponseDTO obtenerPanelAdminCentral() {
        int totalFacultades    = (int) facultadRepository.countByActivaTrue();
        int totalAulasActivas  = (int) aulaRepository.countByActivaTrue();
        int totalUsuarios      = (int) userRepository.countByActivoTrue();
        int pendientes         = (int) reservaRepository.countByStatus(EstadoReserva.PENDIENTE);
        int aceptadas          = (int) reservaRepository.countByStatus(EstadoReserva.ACEPTADA);
        int hoy                = (int) reservaRepository.countByFechaAndEstados(LocalDate.now(), ACTIVOS);

        return new AdminCentralDashboardResponseDTO(
                totalFacultades,
                totalAulasActivas,
                totalUsuarios,
                pendientes,
                aceptadas,
                hoy
        );
    }

    // Verifica que el usuarioId del path coincida con el usuario autenticado.
    // Evita que un ADMIN pueda consultar el panel de otro ADMIN.
    private void validarIdentidad(Long usuarioId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Long idAutenticado = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario autenticado no encontrado"))
                .getId();
        if (!idAutenticado.equals(usuarioId)) {
            throw new OperacionNoPermitidaException("Solo puedes consultar tu propio panel");
        }
    }
}
