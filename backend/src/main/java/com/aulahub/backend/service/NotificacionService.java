package com.aulahub.backend.service;

import com.aulahub.backend.dto.response.NotificacionResponseDTO;
import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.exception.UsuarioNoEncontradoException;
import com.aulahub.backend.model.NotificacionEntity;
import com.aulahub.backend.model.UserEntity;
import com.aulahub.backend.repository.NotificacionRepository;
import com.aulahub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificacionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository notificacionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PushService pushService;

    // Devuelve las notificaciones de un usuario paginadas, más recientes primero.
    // Solo el propio usuario puede consultar sus notificaciones.
    @Transactional(readOnly = true)
    public Page<NotificacionResponseDTO> listarPorUsuario(Long usuarioId, Pageable pageable) {
        validarPropietario(usuarioId);
        return notificacionRepository.findByUsuario_IdOrderByCreatedAtDesc(usuarioId, pageable)
                .map(this::toDTO);
    }

    // Conteo de notificaciones no leídas — para el badge de la campana en el frontend.
    // Solo el propio usuario puede consultar su conteo.
    @Transactional(readOnly = true)
    public int contarNoLeidas(Long usuarioId) {
        validarPropietario(usuarioId);
        return (int) notificacionRepository.countByUsuario_IdAndLeidoFalse(usuarioId);
    }

    // Marca una notificación individual como leída.
    // Solo el propietario de la notificación puede marcarla.
    @Transactional
    public void marcarLeido(Long id) {
        NotificacionEntity notificacion = notificacionRepository.findById(id)
                .orElseThrow(() -> new OperacionNoPermitidaException(
                        "Notificación con id " + id + " no encontrada"));
        validarPropietario(notificacion.getUsuario().getId());
        notificacion.setLeido(true);
        notificacionRepository.save(notificacion);
    }

    // Marca todas las notificaciones de un usuario como leídas en un solo UPDATE.
    // Solo el propio usuario puede ejecutar esta acción.
    @Transactional
    public void marcarTodasLeidas(Long usuarioId) {
        validarPropietario(usuarioId);
        notificacionRepository.marcarTodasLeidasByUsuarioId(usuarioId);
    }

    // Elimina una notificación.
    // Solo el propietario de la notificación puede eliminarla.
    @Transactional
    public void eliminar(Long id) {
        NotificacionEntity notificacion = notificacionRepository.findById(id)
                .orElseThrow(() -> new OperacionNoPermitidaException(
                        "Notificación con id " + id + " no encontrada"));
        validarPropietario(notificacion.getUsuario().getId());
        notificacionRepository.delete(notificacion);
    }

    // Crea una nueva notificación interna para el usuario Y envía un email.
    // Es llamado desde otros services cuando ocurren eventos como:
    // reserva aceptada, reserva rechazada, reserva cancelada o vencida por el sistema.
    @Transactional
    public void crear(Long usuarioId, String titulo, String mensaje, String tipo) {
        userRepository.findById(usuarioId).ifPresent(user -> {
            notificacionRepository.save(
                    NotificacionEntity.builder()
                            .usuario(user)
                            .titulo(titulo)
                            .mensaje(mensaje)
                            .tipo(tipo)
                            .build()
            );

            // El email es complementario: si el envío falla (SMTP no configurado, etc.)
            // no debe revertir la notificación interna ni la acción que la originó.
            // Se registra el error en consola para poder diagnosticar problemas de SMTP.
            try {
                emailService.enviarNotificacionReserva(user.getEmail(), user.getNombre(), titulo, mensaje);
            } catch (Exception e) {
                log.error("Fallo al enviar email de notificación a {}: {}", user.getEmail(), e.getMessage(), e);
            }
            // Push al telefono (FCM), en segundo plano. Best-effort: no bloquea ni rompe si falla.
            pushService.enviarPush(user.getId(), titulo, mensaje);
        });
    }

    // Igual que crear(), pero el email incluye una TABLA con el detalle de la reserva.
    // La notificación interna (campana) guarda solo el mensaje corto.
    @Transactional
    public void crearConDetalle(Long usuarioId, String titulo, String mensaje, String tipo,
                                java.util.Map<String, String> detalles) {
        userRepository.findById(usuarioId).ifPresent(user -> {
            notificacionRepository.save(
                    NotificacionEntity.builder()
                            .usuario(user)
                            .titulo(titulo)
                            .mensaje(mensaje)
                            .tipo(tipo)
                            .build()
            );
            try {
                emailService.enviarNotificacionReservaDetallada(
                        user.getEmail(), user.getNombre(), titulo, mensaje, detalles);
            } catch (Exception e) {
                log.error("Fallo al enviar email de notificación a {}: {}", user.getEmail(), e.getMessage(), e);
            }
            // Push al telefono (FCM), en segundo plano. Best-effort: no bloquea ni rompe si falla.
            pushService.enviarPush(user.getId(), titulo, mensaje);
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    // Verifica que el usuario autenticado sea el propietario del recurso.
    // Lanza OperacionNoPermitidaException si no coinciden.
    private void validarPropietario(Long usuarioId) {
        UserEntity autenticado = getUsuarioAutenticado();
        if (!autenticado.getId().equals(usuarioId)) {
            throw new OperacionNoPermitidaException(
                    "No tienes permiso para acceder a las notificaciones de otro usuario");
        }
    }

    private UserEntity getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario autenticado no encontrado"));
    }

    private NotificacionResponseDTO toDTO(NotificacionEntity n) {
        return new NotificacionResponseDTO(
                n.getId(),
                n.getUsuario().getId(),
                n.getTitulo(),
                n.getMensaje(),
                n.isLeido(),
                n.getTipo(),
                n.getCreatedAt()
        );
    }
}
