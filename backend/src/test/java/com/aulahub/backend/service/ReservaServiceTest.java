package com.aulahub.backend.service;

import com.aulahub.backend.dto.request.ReservaRequestDTO;
import com.aulahub.backend.dto.response.ReservaResponseDTO;
import com.aulahub.backend.exception.AulaInactivaException;
import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.exception.ReservaConflictoException;
import com.aulahub.backend.exception.ReservaEstadoInvalidoException;
import com.aulahub.backend.model.AulaEntity;
import com.aulahub.backend.model.FacultadEntity;
import com.aulahub.backend.model.ReservaEntity;
import com.aulahub.backend.model.RolEntity;
import com.aulahub.backend.model.TipoReservaEntity;
import com.aulahub.backend.model.UserEntity;
import com.aulahub.backend.model.enums.EstadoReserva;
import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.model.enums.Turno;
import com.aulahub.backend.repository.AulaRepository;
import com.aulahub.backend.repository.MateriaRepository;
import com.aulahub.backend.repository.ReservaRepository;
import com.aulahub.backend.repository.RolRepository;
import com.aulahub.backend.repository.TipoReservaRepository;
import com.aulahub.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Nucleo del sistema: creacion de reservas con conflictos, auto-aprobacion por rol,
// rechazo de solapadas al aceptar, permisos de gestion y rechazo automatico de vencidas.
@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    private static final String EMAIL = "usuario@uas.edu.mx";

    @Mock private ReservaRepository reservaRepository;
    @Mock private AulaRepository aulaRepository;
    @Mock private TipoReservaRepository tipoReservaRepository;
    @Mock private MateriaRepository materiaRepository;
    @Mock private UserRepository userRepository;
    @Mock private RolRepository rolRepository;
    @Mock private ActividadLogService actividadLogService;
    @Mock private NotificacionService notificacionService;
    @Mock private RolJerarquiaService rolJerarquia;
    @Mock private FacultadScopeService facultadScope;

    @InjectMocks private ReservaService service;

    private UserEntity usuario;       // autenticado (id 7)
    private FacultadEntity facultad;  // id 1
    private AulaEntity aula;          // id 10, activa
    private TipoReservaEntity tipoClase; // id 20, activo, rolMinimo PROFESOR

    @BeforeEach
    void setUp() {
        usuario = UserEntity.builder().id(7L).email(EMAIL).nombre("Usuario Prueba").activo(true).build();
        facultad = FacultadEntity.builder().id(1L).nombre("FIC").build();
        aula = AulaEntity.builder().id(10L).codigo("LAB-1").nombre("Laboratorio 1")
                .activa(true).facultad(facultad).build();
        tipoClase = TipoReservaEntity.builder().id(20L).clave("CLASE").nombre("Clase")
                .rolMinimo(TipoRol.PROFESOR).prioridad(3).activo(true).facultad(facultad).build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null, List.of()));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void autenticado() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
    }

    private ReservaRequestDTO requestValido() {
        ReservaRequestDTO r = new ReservaRequestDTO();
        r.setAulaId(10L);
        r.setTipoReservaId(20L);
        r.setFecha(LocalDate.now().plusDays(3));
        r.setHoraInicio(LocalTime.of(10, 0));
        r.setHoraFin(LocalTime.of(12, 0));
        r.setTurno(Turno.MATUTINO);
        r.setGrupo("4-1");
        return r;
    }

    private ReservaEntity reserva(EstadoReserva status, UserEntity solicitante) {
        return ReservaEntity.builder()
                .id(100L).solicitante(solicitante).aula(aula).tipoReserva(tipoClase)
                .fecha(LocalDate.now().plusDays(3))
                .horaInicio(LocalTime.of(10, 0)).horaFin(LocalTime.of(12, 0))
                .turno(Turno.MATUTINO).grupo("4-1").status(status)
                .build();
    }

    // Stubs comunes del flujo crear() hasta el chequeo de conflicto
    private void prepararCrear(int rangoUsuario) {
        autenticado();
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula));
        when(tipoReservaRepository.findById(20L)).thenReturn(Optional.of(tipoClase));
        when(rolJerarquia.rangoMaximoUsuario(7L)).thenReturn(rangoUsuario);
        when(rolJerarquia.rango(TipoRol.PROFESOR)).thenReturn(0);
    }

    private void guardarDevuelveLaMismaReserva() {
        when(reservaRepository.save(any(ReservaEntity.class))).thenAnswer(inv -> {
            ReservaEntity r = inv.getArgument(0);
            if (r.getId() == null) r.setId(100L);
            return r;
        });
    }

    // ── crear ────────────────────────────────────────────────────────────────

    @Test
    void noPermiteHoraFinAntesDeLaHoraInicio() {
        ReservaRequestDTO req = requestValido();
        req.setHoraInicio(LocalTime.of(12, 0));
        req.setHoraFin(LocalTime.of(10, 0));

        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    void noPermiteHorasIguales() {
        ReservaRequestDTO req = requestValido();
        req.setHoraInicio(LocalTime.of(10, 0));
        req.setHoraFin(LocalTime.of(10, 0));

        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    void noPermiteReservarUnAulaInactiva() {
        autenticado();
        aula.setActiva(false);
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula));

        assertThatThrownBy(() -> service.crear(requestValido()))
                .isInstanceOf(AulaInactivaException.class);
    }

    @Test
    void soloUnaReservaAceptadaBloqueaElSlot() {
        prepararCrear(0);
        when(reservaRepository.existeConflicto(eq(10L), any(), any(), any(),
                eq(List.of(EstadoReserva.ACEPTADA)))).thenReturn(true);

        assertThatThrownBy(() -> service.crear(requestValido()))
                .isInstanceOf(ReservaConflictoException.class);

        // El conflicto se evalua SOLO contra ACEPTADAS: varias pendientes pueden competir
        verify(reservaRepository).existeConflicto(eq(10L), any(), any(), any(),
                eq(List.of(EstadoReserva.ACEPTADA)));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void reservaDeProfesorEntraPendienteYNotificaALosAdmins() {
        prepararCrear(0);
        when(reservaRepository.existeConflicto(any(), any(), any(), any(), any())).thenReturn(false);
        when(rolJerarquia.esAdminOSuperior(0)).thenReturn(false);
        guardarDevuelveLaMismaReserva();
        when(rolRepository.findByAulaIdAndTipo(10L, TipoRol.ADMIN)).thenReturn(List.of());

        ReservaResponseDTO dto = service.crear(requestValido());

        assertThat(dto.getStatus()).isEqualTo(EstadoReserva.PENDIENTE);
        // Una pendiente nueva no rechaza a nadie
        verify(reservaRepository, never())
                .findSolapadasPorEstado(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reservaDeAdminSeAutoApruebaYRechazaLasPendientesSolapadas() {
        prepararCrear(1); // rango ADMIN
        when(reservaRepository.existeConflicto(any(), any(), any(), any(), any())).thenReturn(false);
        when(rolJerarquia.esAdminOSuperior(1)).thenReturn(true);
        guardarDevuelveLaMismaReserva();

        UserEntity otroProfe = UserEntity.builder().id(99L).nombre("Otro Profe").build();
        ReservaEntity competidora = reserva(EstadoReserva.PENDIENTE, otroProfe);
        competidora.setId(200L);
        when(reservaRepository.findSolapadasPorEstado(eq(10L), any(), any(), any(),
                eq(EstadoReserva.PENDIENTE), any())).thenReturn(List.of(competidora));

        ReservaResponseDTO dto = service.crear(requestValido());

        assertThat(dto.getStatus()).isEqualTo(EstadoReserva.ACEPTADA);
        assertThat(competidora.getStatus()).isEqualTo(EstadoReserva.RECHAZADA);
        assertThat(competidora.getMotivoRechazo()).isNotBlank();
        // El solicitante de la competidora recibe la notificacion del rechazo
        verify(notificacionService).crear(eq(99L), anyString(), anyString(), anyString());
    }

    @Test
    void elTipoDeReservaExigeUnRolMinimo() {
        autenticado();
        TipoReservaEntity tipoEvento = TipoReservaEntity.builder()
                .id(21L).clave("EVENTO").nombre("Evento")
                .rolMinimo(TipoRol.ADMIN).prioridad(4).activo(true).facultad(facultad).build();
        when(aulaRepository.findById(10L)).thenReturn(Optional.of(aula));
        when(tipoReservaRepository.findById(20L)).thenReturn(Optional.of(tipoEvento));
        when(rolJerarquia.rangoMaximoUsuario(7L)).thenReturn(0);  // PROFESOR
        when(rolJerarquia.rango(TipoRol.ADMIN)).thenReturn(1);

        assertThatThrownBy(() -> service.crear(requestValido()))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    // ── aceptar / rechazar ───────────────────────────────────────────────────

    @Test
    void aceptarUnaPendienteRechazaLasDemasQueCompetianPorElSlot() {
        autenticado();
        when(rolRepository.findByUsuarioId(7L))
                .thenReturn(List.of(RolEntity.builder().tipo(TipoRol.ADMIN_CENTRAL).build()));

        ReservaEntity pendiente = reserva(EstadoReserva.PENDIENTE, usuario);
        when(reservaRepository.findById(100L)).thenReturn(Optional.of(pendiente));
        guardarDevuelveLaMismaReserva();

        UserEntity otroProfe = UserEntity.builder().id(99L).nombre("Otro Profe").build();
        ReservaEntity competidora = reserva(EstadoReserva.PENDIENTE, otroProfe);
        competidora.setId(200L);
        when(reservaRepository.findSolapadasPorEstado(eq(10L), any(), any(), any(),
                eq(EstadoReserva.PENDIENTE), eq(100L))).thenReturn(List.of(competidora));

        ReservaResponseDTO dto = service.aceptar(100L);

        assertThat(dto.getStatus()).isEqualTo(EstadoReserva.ACEPTADA);
        assertThat(competidora.getStatus()).isEqualTo(EstadoReserva.RECHAZADA);
        verify(notificacionService).crear(eq(99L), anyString(), anyString(), anyString());
        // El dueno de la aceptada recibe su confirmacion con detalle
        verify(notificacionService).crearConDetalle(eq(7L), anyString(), anyString(), anyString(), any());
    }

    @Test
    void soloSePuedeAceptarUnaReservaPendiente() {
        autenticado();
        when(rolRepository.findByUsuarioId(7L))
                .thenReturn(List.of(RolEntity.builder().tipo(TipoRol.ADMIN_CENTRAL).build()));
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.ACEPTADA, usuario)));

        assertThatThrownBy(() -> service.aceptar(100L))
                .isInstanceOf(ReservaEstadoInvalidoException.class);
    }

    @Test
    void unAdminNoPuedeGestionarReservasDeAulasQueNoTieneAsignadas() {
        autenticado();
        AulaEntity otraAula = AulaEntity.builder().id(55L).nombre("Otra").facultad(facultad).build();
        RolEntity rolAdmin = RolEntity.builder()
                .tipo(TipoRol.ADMIN).aulas(List.of(otraAula)).build();
        when(rolRepository.findByUsuarioId(7L)).thenReturn(List.of(rolAdmin));
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.PENDIENTE, usuario)));

        assertThatThrownBy(() -> service.aceptar(100L))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    void unAdminDeOtroTurnoNoPuedeGestionarLaReserva() {
        autenticado();
        // Tiene el aula correcta pero su turno es VESPERTINO y la reserva es MATUTINA
        RolEntity rolAdmin = RolEntity.builder()
                .tipo(TipoRol.ADMIN).aulas(List.of(aula)).turno(Turno.VESPERTINO).build();
        when(rolRepository.findByUsuarioId(7L)).thenReturn(List.of(rolAdmin));
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.PENDIENTE, usuario)));

        assertThatThrownBy(() -> service.aceptar(100L))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    void rechazarGuardaElMotivoYNotificaAlSolicitante() {
        autenticado();
        when(rolRepository.findByUsuarioId(7L))
                .thenReturn(List.of(RolEntity.builder().tipo(TipoRol.ADMIN_CENTRAL).build()));
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.PENDIENTE, usuario)));
        guardarDevuelveLaMismaReserva();

        ReservaResponseDTO dto = service.rechazar(100L, "No hay disponibilidad");

        assertThat(dto.getStatus()).isEqualTo(EstadoReserva.RECHAZADA);
        assertThat(dto.getMotivoRechazo()).isEqualTo("No hay disponibilidad");
        verify(notificacionService).crearConDetalle(eq(7L), anyString(), anyString(), anyString(), any());
    }

    // ── cancelar ─────────────────────────────────────────────────────────────

    @Test
    void unProfesorNoPuedeCancelarLaReservaDeOtro() {
        autenticado();
        UserEntity otro = UserEntity.builder().id(99L).nombre("Otro").build();
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.PENDIENTE, otro)));
        when(rolJerarquia.rangoMaximoUsuario(7L)).thenReturn(0);
        when(rolJerarquia.esAdminOSuperior(0)).thenReturn(false);

        assertThatThrownBy(() -> service.cancelar(100L))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    void elDuenoPuedeCancelarSuPropiaReserva() {
        autenticado();
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.PENDIENTE, usuario)));
        when(rolJerarquia.rangoMaximoUsuario(7L)).thenReturn(0);
        when(rolJerarquia.esAdminOSuperior(0)).thenReturn(false);
        guardarDevuelveLaMismaReserva();

        ReservaResponseDTO dto = service.cancelar(100L);

        assertThat(dto.getStatus()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void noSePuedeCancelarUnaReservaYaRechazada() {
        when(reservaRepository.findById(100L))
                .thenReturn(Optional.of(reserva(EstadoReserva.RECHAZADA, usuario)));

        assertThatThrownBy(() -> service.cancelar(100L))
                .isInstanceOf(ReservaEstadoInvalidoException.class);
    }

    // ── rechazo automatico de vencidas (scheduler) ───────────────────────────

    @Test
    void lasPendientesVencidasSeRechazanAutomaticamente() {
        ReservaEntity vencida = reserva(EstadoReserva.PENDIENTE, usuario);
        vencida.setFecha(LocalDate.now().minusDays(1));
        when(reservaRepository.findByStatusAndFechaBefore(eq(EstadoReserva.PENDIENTE), any()))
                .thenReturn(List.of(vencida));

        service.rechazarVencidas();

        assertThat(vencida.getStatus()).isEqualTo(EstadoReserva.RECHAZADA);
        assertThat(vencida.getMotivoRechazo()).isNotBlank();
        verify(notificacionService).crearConDetalle(eq(7L), anyString(), anyString(), anyString(), any());
        verify(reservaRepository).saveAll(List.of(vencida));
    }
}
