package com.aulahub.backend.service;

import com.aulahub.backend.dto.request.ReservaRequestDTO;
import com.aulahub.backend.dto.response.ReservaResponseDTO;
import com.aulahub.backend.exception.AulaInactivaException;
import com.aulahub.backend.exception.AulaNoEncontradaException;
import com.aulahub.backend.exception.MateriaNoEncontradaException;
import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.exception.ReservaConflictoException;
import com.aulahub.backend.exception.ReservaEstadoInvalidoException;
import com.aulahub.backend.exception.ReservaNoEncontradaException;
import com.aulahub.backend.exception.TipoReservaInactivoException;
import com.aulahub.backend.exception.TipoReservaNoEncontradoException;
import com.aulahub.backend.exception.UsuarioNoEncontradoException;
import com.aulahub.backend.model.AulaEntity;
import com.aulahub.backend.model.MateriaEntity;
import com.aulahub.backend.model.ReservaEntity;
import com.aulahub.backend.model.TipoReservaEntity;
import com.aulahub.backend.model.UserEntity;
import com.aulahub.backend.model.RolEntity;
import com.aulahub.backend.model.enums.EstadoReserva;
import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.model.enums.Turno;
import com.aulahub.backend.repository.AulaRepository;
import com.aulahub.backend.repository.MateriaRepository;
import com.aulahub.backend.repository.ReservaRepository;
import com.aulahub.backend.repository.RolRepository;
import com.aulahub.backend.repository.TipoReservaRepository;
import com.aulahub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final AulaRepository aulaRepository;
    private final TipoReservaRepository tipoReservaRepository;
    private final MateriaRepository materiaRepository;
    private final UserRepository userRepository;
    private final RolRepository rolRepository;
    private final ActividadLogService actividadLogService;
    private final NotificacionService notificacionService;
    private final RolJerarquiaService rolJerarquia;
    private final FacultadScopeService facultadScope;

    // Una reserva PENDIENTE o ACEPTADA bloquea el horario; RECHAZADA/CANCELADA lo liberan
    private static final List<EstadoReserva> ESTADOS_ACTIVOS =
            List.of(EstadoReserva.PENDIENTE, EstadoReserva.ACEPTADA);

    // Formato mexicano para las fechas que se muestran en notificaciones y logs
    private static final DateTimeFormatter FECHA_MX = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // Devuelve reservas paginadas. Si el usuario autenticado es SUPER_ADMIN,
    // filtra automáticamente por su facultad (RLS).
    @Transactional(readOnly = true)
    public Page<ReservaResponseDTO> listar(Pageable pageable) {
        Long facultadId = facultadScope.getFacultadIdSuperAdmin();
        if (facultadId != null) {
            return reservaRepository.findByFacultadId(facultadId, pageable).map(this::toDTO);
        }
        return reservaRepository.findAll(pageable).map(this::toDTO);
    }

    // Devuelve todas las reservas de un usuario específico paginadas
    @Transactional(readOnly = true)
    public Page<ReservaResponseDTO> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return reservaRepository.findBySolicitante_Id(usuarioId, pageable).map(this::toDTO);
    }

    // Devuelve todas las reservas de un aula específica paginadas
    @Transactional(readOnly = true)
    public Page<ReservaResponseDTO> listarPorAula(Long aulaId, Pageable pageable) {
        return reservaRepository.findByAula_Id(aulaId, pageable).map(this::toDTO);
    }

    // Devuelve las reservas activas (PENDIENTE o ACEPTADA) de un aula en un rango de fechas
    // Alimenta la cuadrícula semanal del calendario: el frontend pinta como ocupados estos slots
    @Transactional(readOnly = true)
    public List<ReservaResponseDTO> listarCalendario(Long aulaId, LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new OperacionNoPermitidaException("La fecha final no puede ser anterior a la inicial");
        }
        return reservaRepository
                .findByAula_IdAndFechaBetweenAndStatusInOrderByFechaAscHoraInicioAsc(
                        aulaId, desde, hasta, ESTADOS_ACTIVOS)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // Busca y devuelve una reserva por su ID
    // Lanza ReservaNoEncontradaException si no existe
    @Transactional(readOnly = true)
    public ReservaResponseDTO obtenerPorId(Long id) {
        return toDTO(buscarOLanzar(id));
    }

    // Crea una reserva nueva en estado PENDIENTE
    // Valida que el aula esté activa, el tipo de reserva esté activo
    // y que no exista conflicto de horario para esa aula en esa fecha y rango de horas
    @Transactional
    public ReservaResponseDTO crear(ReservaRequestDTO request) {
        if (!request.getHoraInicio().isBefore(request.getHoraFin())) {
            throw new OperacionNoPermitidaException("La hora de fin debe ser posterior a la hora de inicio");
        }

        UserEntity solicitante = usuarioActual();

        AulaEntity aula = aulaRepository.findById(request.getAulaId())
                .orElseThrow(() -> new AulaNoEncontradaException(
                        "Aula con id " + request.getAulaId() + " no encontrada"));
        if (!aula.isActiva()) {
            throw new AulaInactivaException("El aula " + aula.getNombre() + " está inactiva");
        }

        TipoReservaEntity tipoReserva = tipoReservaRepository.findById(request.getTipoReservaId())
                .orElseThrow(() -> new TipoReservaNoEncontradoException(
                        "Tipo de reserva con id " + request.getTipoReservaId() + " no encontrado"));
        if (!tipoReserva.isActivo()) {
            throw new TipoReservaInactivoException(
                    "El tipo de reserva " + tipoReserva.getNombre() + " está inactivo");
        }

        // El rol del solicitante debe alcanzar el rol mínimo exigido por el tipo de reserva
        int rangoSolicitante = rolJerarquia.rangoMaximoUsuario(solicitante.getId());
        if (rangoSolicitante < rolJerarquia.rango(tipoReserva.getRolMinimo())) {
            throw new OperacionNoPermitidaException(
                    "Tu rol no puede crear reservas del tipo " + tipoReserva.getNombre());
        }

        // materia es opcional — EVENTO y MANTENIMIENTO no la requieren
        MateriaEntity materia = null;
        if (request.getMateriaId() != null) {
            materia = materiaRepository.findById(request.getMateriaId())
                    .orElseThrow(() -> new MateriaNoEncontradaException(
                            "Materia con id " + request.getMateriaId() + " no encontrada"));
        }

        // Solo una reserva ya ACEPTADA bloquea el horario; varias PENDIENTES pueden competir
        // por el mismo slot y el admin decide cuál acepta.
        if (reservaRepository.existeConflicto(aula.getId(), request.getFecha(),
                request.getHoraInicio(), request.getHoraFin(), List.of(EstadoReserva.ACEPTADA))) {
            throw new ReservaConflictoException(
                    "El aula ya tiene una reserva aprobada el " + request.getFecha().format(FECHA_MX)
                            + " entre " + request.getHoraInicio() + " y " + request.getHoraFin());
        }

        // Las reservas creadas por ADMIN o superior se aprueban automáticamente;
        // las de un PROFESOR quedan PENDIENTE hasta que un admin las revise.
        boolean autoAprobada = rolJerarquia.esAdminOSuperior(rangoSolicitante);
        EstadoReserva estadoInicial = autoAprobada ? EstadoReserva.ACEPTADA : EstadoReserva.PENDIENTE;

        ReservaEntity reserva = ReservaEntity.builder()
                .solicitante(solicitante)
                .aula(aula)
                .tipoReserva(tipoReserva)
                .materia(materia)
                .grupo(request.getGrupo())
                .turno(request.getTurno())
                .fecha(request.getFecha())
                .horaInicio(request.getHoraInicio())
                .horaFin(request.getHoraFin())
                .esExterno(Boolean.TRUE.equals(request.getEsExterno()))
                .status(estadoInicial)
                .build();

        ReservaEntity guardada = reservaRepository.save(reserva);

        // Si entró directo como ACEPTADA (admin), rechaza las pendientes que competían por el slot
        if (autoAprobada) {
            rechazarSolapadasPendientes(guardada);
        } else {
            // Si quedó PENDIENTE (la creó un profesor), avisa a los admins de esa aula
            notificarAdminsDelAula(guardada);
        }

        actividadLogService.registrarAccionActual(
                "CREAR_RESERVA", "reserva", guardada.getId(),
                "Reserva " + (autoAprobada ? "creada y aprobada" : "solicitada") + " en " + aula.getNombre()
                        + " el " + guardada.getFecha().format(FECHA_MX) + " ("
                        + guardada.getHoraInicio() + " - " + guardada.getHoraFin() + ")");

        return toDTO(guardada);
    }

    // Cambia el estado de una reserva de PENDIENTE a ACEPTADA
    // Lanza ReservaEstadoInvalidoException si la reserva no está en PENDIENTE
    @Transactional
    public ReservaResponseDTO aceptar(Long id) {
        ReservaEntity reserva = buscarOLanzar(id);
        validarPuedeGestionarReserva(reserva);
        if (reserva.getStatus() != EstadoReserva.PENDIENTE) {
            throw new ReservaEstadoInvalidoException(
                    "Solo se puede aceptar una reserva en estado PENDIENTE (estado actual: "
                            + reserva.getStatus() + ")");
        }
        reserva.setStatus(EstadoReserva.ACEPTADA);
        ReservaEntity actualizada = reservaRepository.save(reserva);

        // Al aceptar una, las demás solicitudes pendientes que competían por el mismo slot se rechazan
        rechazarSolapadasPendientes(actualizada);

        actividadLogService.registrarAccionActual(
                "APROBAR_RESERVA", "reserva", actualizada.getId(),
                "Reserva aceptada en " + actualizada.getAula().getNombre() + " el "
                        + actualizada.getFecha().format(FECHA_MX));

        notificacionService.crearConDetalle(
                actualizada.getSolicitante().getId(),
                "Reserva aceptada",
                "Tu solicitud de reserva fue aceptada. Estos son los detalles:",
                "RESERVA_ACEPTADA",
                detalleParaEmail(actualizada));

        return toDTO(actualizada);
    }

    // Cambia el estado de una reserva de PENDIENTE a RECHAZADA y guarda el motivo
    // Lanza ReservaEstadoInvalidoException si la reserva no está en PENDIENTE
    @Transactional
    public ReservaResponseDTO rechazar(Long id, String motivo) {
        ReservaEntity reserva = buscarOLanzar(id);
        validarPuedeGestionarReserva(reserva);
        if (reserva.getStatus() != EstadoReserva.PENDIENTE) {
            throw new ReservaEstadoInvalidoException(
                    "Solo se puede rechazar una reserva en estado PENDIENTE (estado actual: "
                            + reserva.getStatus() + ")");
        }
        reserva.setStatus(EstadoReserva.RECHAZADA);
        reserva.setMotivoRechazo(motivo);
        ReservaEntity actualizada = reservaRepository.save(reserva);

        actividadLogService.registrarAccionActual(
                "RECHAZAR_RESERVA", "reserva", actualizada.getId(),
                "Reserva rechazada en " + actualizada.getAula().getNombre() + " el "
                        + actualizada.getFecha().format(FECHA_MX) + " — motivo: " + motivo);

        Map<String, String> detalleRechazo = detalleParaEmail(actualizada);
        detalleRechazo.put("Motivo del rechazo", motivo);
        notificacionService.crearConDetalle(
                actualizada.getSolicitante().getId(),
                "Reserva rechazada",
                "Tu solicitud de reserva fue rechazada. Estos son los detalles:",
                "RESERVA_RECHAZADA",
                detalleRechazo);

        return toDTO(actualizada);
    }

    // Cambia el estado de una reserva a CANCELADA
    // Lanza ReservaEstadoInvalidoException si ya fue rechazada o cancelada
    @Transactional
    public ReservaResponseDTO cancelar(Long id) {
        ReservaEntity reserva = buscarOLanzar(id);
        if (reserva.getStatus() == EstadoReserva.RECHAZADA
                || reserva.getStatus() == EstadoReserva.CANCELADA) {
            throw new ReservaEstadoInvalidoException(
                    "No se puede cancelar una reserva en estado " + reserva.getStatus());
        }

        // Un PROFESOR solo puede cancelar SU propia reserva; ADMIN o superior puede cancelar cualquiera
        UserEntity actual = usuarioActual();
        boolean esDueno = reserva.getSolicitante().getId().equals(actual.getId());
        boolean esAdmin = rolJerarquia.esAdminOSuperior(rolJerarquia.rangoMaximoUsuario(actual.getId()));
        if (!esDueno && !esAdmin) {
            throw new OperacionNoPermitidaException("No puedes cancelar una reserva que no es tuya");
        }

        reserva.setStatus(EstadoReserva.CANCELADA);
        ReservaEntity actualizada = reservaRepository.save(reserva);

        actividadLogService.registrarAccionActual(
                "CANCELAR_RESERVA", "reserva", actualizada.getId(),
                "Reserva cancelada en " + actualizada.getAula().getNombre() + " el "
                        + actualizada.getFecha().format(FECHA_MX));

        notificacionService.crearConDetalle(
                actualizada.getSolicitante().getId(),
                "Reserva cancelada",
                "Tu reserva fue cancelada. Estos son los detalles:",
                "RESERVA_CANCELADA",
                detalleParaEmail(actualizada));

        return toDTO(actualizada);
    }

    // Rechaza automáticamente las reservas PENDIENTES cuya fecha YA PASÓ (fecha < hoy).
    // Las reservas futuras (ej. una del viernes vista en lunes) NO se tocan.
    // A cada una: la marca RECHAZADA, manda correo con el detalle y registra la acción en el log.
    // Es invocado por ReservaScheduler.
    @Transactional
    public void rechazarVencidas() {
        List<ReservaEntity> vencidas = reservaRepository
                .findByStatusAndFechaBefore(EstadoReserva.PENDIENTE, LocalDate.now());
        vencidas.forEach(this::rechazarPorSistema);
        reservaRepository.saveAll(vencidas);
    }

    // Marca una reserva como rechazada por el sistema: correo con detalle + registro en log.
    private void rechazarPorSistema(ReservaEntity reserva) {
        reserva.setStatus(EstadoReserva.RECHAZADA);
        reserva.setMotivoRechazo("Rechazada automáticamente por el sistema porque no se atendió a tiempo");

        // Correo con la tabla de detalle (igual que el rechazo manual)
        Map<String, String> detalle = detalleParaEmail(reserva);
        detalle.put("Motivo del rechazo", "No fue atendida antes de la fecha reservada");
        notificacionService.crearConDetalle(
                reserva.getSolicitante().getId(),
                "Reserva rechazada automáticamente",
                "Tu reserva fue rechazada automáticamente porque no se atendió a tiempo. Detalles:",
                "SISTEMA",
                detalle);

        // Registro en actividad log (atribuido al solicitante; el scheduler no tiene sesión)
        actividadLogService.registrarParaUsuario(
                reserva.getSolicitante(),
                "RECHAZO_AUTOMATICO", "reserva", reserva.getId(),
                "Reserva en " + reserva.getAula().getNombre() + " del "
                        + reserva.getFecha().format(FECHA_MX)
                        + " rechazada automáticamente por el sistema (no atendida a tiempo)");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    // Avisa (notificacion interna + email) a los admins responsables del aula cuando
    // un profesor crea una solicitud PENDIENTE. Solo notifica al admin cuyo turno
    // coincide con la reserva (o que gestiona ambos turnos).
    private void notificarAdminsDelAula(ReservaEntity reserva) {
        List<RolEntity> adminsDelAula = rolRepository.findByAulaIdAndTipo(
                reserva.getAula().getId(), TipoRol.ADMIN);

        for (RolEntity rolAdmin : adminsDelAula) {
            Turno turnoAdmin = rolAdmin.getTurno();
            // Si el admin gestiona un turno distinto y no es AMBOS, no le incumbe esta reserva
            if (turnoAdmin != null && turnoAdmin != Turno.AMBOS && turnoAdmin != reserva.getTurno()) {
                continue;
            }
            UserEntity admin = rolAdmin.getUsuario();
            if (admin == null) continue;

            notificacionService.crear(
                    admin.getId(),
                    "Nueva solicitud de reserva",
                    reserva.getSolicitante().getNombre() + " solicitó reservar "
                            + reserva.getAula().getNombre() + " el "
                            + reserva.getFecha().format(FECHA_MX) + " ("
                            + reserva.getHoraInicio() + " - " + reserva.getHoraFin()
                            + "). Revísala en tus solicitudes pendientes.",
                    "SISTEMA");
        }
    }

    // Rechaza las solicitudes PENDIENTES que solapan el mismo aula/fecha/horario que la reserva
    // recién aceptada. Notifica a cada solicitante. Se usa al aprobar y al auto-aprobar (admin).
    private void rechazarSolapadasPendientes(ReservaEntity aceptada) {
        List<ReservaEntity> solapadas = reservaRepository.findSolapadasPorEstado(
                aceptada.getAula().getId(), aceptada.getFecha(),
                aceptada.getHoraInicio(), aceptada.getHoraFin(),
                EstadoReserva.PENDIENTE, aceptada.getId());

        for (ReservaEntity r : solapadas) {
            r.setStatus(EstadoReserva.RECHAZADA);
            r.setMotivoRechazo("Se aceptó otra solicitud para este horario");
            notificacionService.crear(
                    r.getSolicitante().getId(),
                    "Reserva rechazada",
                    "Tu solicitud del " + r.getFecha().format(FECHA_MX) + " en " + r.getAula().getNombre()
                            + " fue rechazada porque se aceptó otra reserva para ese horario.",
                    "RESERVA_RECHAZADA");
        }

        if (!solapadas.isEmpty()) reservaRepository.saveAll(solapadas);
    }

    // Valida que el usuario autenticado pueda gestionar (aceptar/rechazar) la reserva:
    //  - ADMIN_CENTRAL: cualquier reserva
    //  - SUPER_ADMIN: reservas de su facultad
    //  - ADMIN: solo reservas de las aulas que tiene asignadas (y, si tiene turno
    //    específico, del mismo turno de la reserva)
    private void validarPuedeGestionarReserva(ReservaEntity reserva) {
        UserEntity actual = usuarioActual();
        List<RolEntity> roles = rolRepository.findByUsuarioId(actual.getId());

        // ADMIN_CENTRAL: sin restricción
        if (roles.stream().anyMatch(r -> r.getTipo() == TipoRol.ADMIN_CENTRAL)) return;

        Long facultadReserva = reserva.getAula().getFacultad().getId();

        // SUPER_ADMIN: cualquier aula de su facultad
        boolean esSuperDeLaFacultad = roles.stream()
                .filter(r -> r.getTipo() == TipoRol.SUPER_ADMIN && r.getFacultad() != null)
                .anyMatch(r -> r.getFacultad().getId().equals(facultadReserva));
        if (esSuperDeLaFacultad) return;

        // ADMIN: el aula de la reserva debe estar entre sus aulas asignadas
        // y, si su rol tiene turno definido (no AMBOS), debe coincidir con el de la reserva.
        boolean esAdminDelAula = roles.stream()
                .filter(r -> r.getTipo() == TipoRol.ADMIN)
                .anyMatch(r -> {
                    boolean tieneElAula = r.getAulas().stream()
                            .anyMatch(a -> a.getId().equals(reserva.getAula().getId()));
                    boolean turnoOk = r.getTurno() == null
                            || r.getTurno() == Turno.AMBOS
                            || r.getTurno() == reserva.getTurno();
                    return tieneElAula && turnoOk;
                });
        if (esAdminDelAula) return;

        throw new OperacionNoPermitidaException(
                "No tienes permiso para gestionar reservas de esta aula");
    }

    // Construye el detalle (etiqueta -> valor) de una reserva para la tabla del email.
    private Map<String, String> detalleParaEmail(ReservaEntity r) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("Aula", r.getAula().getNombre());
        d.put("Facultad", r.getAula().getFacultad().getNombre());
        d.put("Fecha", r.getFecha().format(FECHA_MX));
        d.put("Horario", r.getHoraInicio() + " - " + r.getHoraFin());
        if (r.getTurno() != null) d.put("Turno", r.getTurno().name());
        d.put("Tipo de reserva", r.getTipoReserva().getNombre());
        if (r.getMateria() != null) d.put("Materia", r.getMateria().getNombre());
        if (r.getGrupo() != null && !r.getGrupo().isBlank()) d.put("Grupo", r.getGrupo());
        d.put("Solicitante", r.getSolicitante().getNombre());
        return d;
    }

    private ReservaEntity buscarOLanzar(Long id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new ReservaNoEncontradaException(
                        "Reserva con id " + id + " no encontrada"));
    }

    private UserEntity usuarioActual() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsuarioNoEncontradoException(
                        "Usuario autenticado no encontrado: " + email));
    }

    private ReservaResponseDTO toDTO(ReservaEntity r) {
        return new ReservaResponseDTO(
                r.getId(),
                r.getSolicitante().getId(),
                r.getSolicitante().getNombre(),
                r.getAula().getId(),
                r.getAula().getNombre(),
                r.getTipoReserva().getId(),
                r.getTipoReserva().getNombre(),
                r.getTipoReserva().getClave(),
                r.getTipoReserva().getPrioridad(),
                r.getMateria() != null ? r.getMateria().getId() : null,
                r.getMateria() != null ? r.getMateria().getNombre() : null,
                r.getGrupo(),
                r.getTurno(),
                r.getFecha(),
                r.getHoraInicio(),
                r.getHoraFin(),
                r.isEsExterno(),
                r.getStatus(),
                r.getMotivoRechazo(),
                r.getCreatedAt()
        );
    }
}
