package com.aulahub.backend.service;

import com.aulahub.backend.dto.request.RolRequestDTO;
import com.aulahub.backend.dto.response.AulaResponseDTO;
import com.aulahub.backend.dto.response.RolResponseDTO;
import com.aulahub.backend.exception.FacultadNoEncontradaException;
import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.exception.RolNoEncontradoException;
import com.aulahub.backend.exception.UsuarioNoEncontradoException;
import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.model.FacultadEntity;
import com.aulahub.backend.model.RolEntity;
import com.aulahub.backend.model.UserEntity;
import com.aulahub.backend.model.AulaEntity;
import com.aulahub.backend.repository.AulaRepository;
import com.aulahub.backend.repository.FacultadRepository;
import com.aulahub.backend.repository.RolRepository;
import com.aulahub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RolService {

    private final RolRepository rolRepository;
    private final UserRepository userRepository;
    private final FacultadRepository facultadRepository;
    private final AulaRepository aulaRepository;
    private final ActividadLogService actividadLogService;

    // Devuelve la lista de todos los roles asignados en el sistema
    @Transactional(readOnly = true)
    public List<RolResponseDTO> listar() {
        return rolRepository.findAll().stream().map(this::toDTO).toList();
    }

    // Devuelve los roles del usuario que está haciendo la petición (cualquier rol autenticado)
    // Usado en el frontend para saber a qué facultades pertenece el usuario actual
    @Transactional(readOnly = true)
    public List<RolResponseDTO> listarMisRoles() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .map(user -> rolRepository.findByUsuarioId(user.getId())
                        .stream()
                        .map(this::toDTO)
                        .toList())
                .orElse(List.of());
    }

    // Devuelve todos los roles de una facultad
    // SUPER_ADMIN solo ve ADMIN y PROFESOR (sus subordinados), ADMIN_CENTRAL ve todos
    @Transactional(readOnly = true)
    public List<RolResponseDTO> listarPorFacultad(Long facultadId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean esSuperAdmin = userRepository.findByEmail(email)
                .map(u -> rolRepository.findByUsuarioId(u.getId()).stream()
                        .anyMatch(r -> r.getTipo() == TipoRol.SUPER_ADMIN))
                .orElse(false);

        return rolRepository.findByFacultadId(facultadId).stream()
                .filter(r -> !esSuperAdmin ||
                        r.getTipo() == TipoRol.ADMIN || r.getTipo() == TipoRol.PROFESOR)
                .map(this::toDTO)
                .toList();
    }

    // Devuelve todos los roles asignados a un usuario específico
    @Transactional(readOnly = true)
    public List<RolResponseDTO> listarPorUsuario(Long usuarioId) {
        return rolRepository.findByUsuarioId(usuarioId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // Asigna un nuevo rol a un usuario
    // Lanza UsuarioNoEncontradoException si el usuario no existe
    // Lanza FacultadNoEncontradaException si la facultad no existe
    @Transactional
    public RolResponseDTO asignar(RolRequestDTO request) {
        UserEntity usuario = userRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario con id " + request.getUsuarioId() + " no encontrado"));

        // Reglas de reemplazo:
        // - PROFESOR → puede tener múltiples facultades: solo borra el rol de ESA facultad
        // - ADMIN / SUPER_ADMIN / ADMIN_CENTRAL → un solo rol por usuario: borra TODOS los anteriores
        if (request.getTipo() == TipoRol.PROFESOR) {
            if (request.getFacultadId() != null) {
                rolRepository.deleteByUsuarioIdAndFacultadId(request.getUsuarioId(), request.getFacultadId());
            }
        } else {
            List<RolEntity> rolesAnteriores = rolRepository.findByUsuarioId(request.getUsuarioId());
            if (!rolesAnteriores.isEmpty()) rolRepository.deleteAll(rolesAnteriores);
        }

        RolEntity.RolEntityBuilder rolBuilder = RolEntity.builder()
                .usuario(usuario)
                .tipo(request.getTipo());

        if (request.getTipo() != TipoRol.ADMIN_CENTRAL && request.getFacultadId() == null) {
            throw new OperacionNoPermitidaException("La facultad es obligatoria para el rol " + request.getTipo());
        }

        if (request.getFacultadId() != null) {
            FacultadEntity facultad = facultadRepository.findById(request.getFacultadId())
                    .orElseThrow(() -> new FacultadNoEncontradaException("Facultad con id " + request.getFacultadId() + " no encontrada"));
            rolBuilder.facultad(facultad);
        }

        if (request.getTurno() != null) {
            rolBuilder.turno(request.getTurno());
        }

        // Aulas asignadas — solo aplica para ADMIN
        if (request.getAulaIds() != null && !request.getAulaIds().isEmpty()) {
            List<AulaEntity> aulas = aulaRepository.findAllById(request.getAulaIds());
            rolBuilder.aulas(aulas);
        }

        RolEntity saved = rolRepository.save(rolBuilder.build());

        String detalleFacultad = saved.getFacultad() != null
                ? " en " + saved.getFacultad().getNombre() + " (" + saved.getFacultad().getClave() + ")"
                : "";
        String detalleTurno = saved.getTurno() != null ? " — turno " + saved.getTurno() : "";
        actividadLogService.registrarAccionActual(
                "ASIGNAR_ROL", "rol", saved.getId(),
                "Rol " + saved.getTipo() + " asignado a " + usuario.getNombre()
                        + " (" + usuario.getEmail() + ")" + detalleFacultad + detalleTurno);

        return toDTO(saved);
    }

    // Quita una aula específica de un rol ADMIN
    @Transactional
    public void eliminarAula(Long rolId, Long aulaId) {
        RolEntity rol = rolRepository.findById(rolId)
                .orElseThrow(() -> new RolNoEncontradoException("Rol con id " + rolId + " no encontrado"));
        rol.getAulas().removeIf(a -> a.getId().equals(aulaId));
        rolRepository.save(rol);
    }

    // Elimina (revoca) un rol asignado a un usuario
    // Lanza RolNoEncontradoException si el rol no existe
    @Transactional
    public void eliminar(Long id) {
        RolEntity rol = rolRepository.findById(id)
                .orElseThrow(() -> new RolNoEncontradoException("Rol con id " + id + " no encontrado"));

        // Capturar info antes de borrar para el log
        String detalle = "Rol " + rol.getTipo()
                + " revocado de " + rol.getUsuario().getNombre()
                + " (" + rol.getUsuario().getEmail() + ")"
                + (rol.getFacultad() != null
                    ? " en " + rol.getFacultad().getNombre() + " (" + rol.getFacultad().getClave() + ")"
                    : "");

        rolRepository.delete(rol);
        actividadLogService.registrarAccionActual("ELIMINAR_ROL", "rol", id, detalle);
    }

    private RolResponseDTO toDTO(RolEntity rol) {
        List<AulaResponseDTO> aulas = rol.getAulas().stream()
                .map(a -> new AulaResponseDTO(a.getId(), a.getFacultad().getId(), a.getCodigo(), a.getNombre(), a.isActiva(), a.getCapacidad(), a.getImagenUrl()))
                .toList();

        return new RolResponseDTO(
                rol.getId(),
                rol.getUsuario().getId(),
                rol.getUsuario().getNombre(),
                rol.getUsuario().getEmail(),
                rol.getFacultad() != null ? rol.getFacultad().getId()     : null,
                rol.getFacultad() != null ? rol.getFacultad().getNombre() : null,
                rol.getTipo(),
                rol.getTurno(),
                aulas
        );
    }
}
