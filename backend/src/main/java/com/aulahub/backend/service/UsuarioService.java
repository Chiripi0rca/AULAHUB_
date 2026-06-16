package com.aulahub.backend.service;

import com.aulahub.backend.dto.request.AsignarGrupoMateriasRequestDTO;
import com.aulahub.backend.dto.request.UpdatePerfilRequestDTO;
import com.aulahub.backend.dto.request.UpdateUserRequestDTO;
import com.aulahub.backend.dto.response.AsignacionMaestroResponseDTO;
import com.aulahub.backend.dto.response.MateriaResponseDTO;
import com.aulahub.backend.dto.response.UsuarioResponseDTO;
import com.aulahub.backend.exception.GrupoNoEncontradoException;
import com.aulahub.backend.exception.MateriaNoEncontradaException;
import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.exception.UsuarioNoEncontradoException;
import com.aulahub.backend.model.AsignacionMaestroEntity;
import com.aulahub.backend.model.GrupoEntity;
import com.aulahub.backend.model.MateriaEntity;
import com.aulahub.backend.model.UserEntity;
import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.repository.AsignacionMaestroRepository;
import com.aulahub.backend.repository.GrupoRepository;
import com.aulahub.backend.repository.MateriaRepository;
import com.aulahub.backend.repository.RolRepository;
import com.aulahub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UserRepository userRepository;
    private final RolRepository rolRepository;
    private final MateriaRepository materiaRepository;
    private final GrupoRepository grupoRepository;
    private final AsignacionMaestroRepository asignacionRepository;
    private final ActividadLogService actividadLogService;

    // Devuelve la lista de todos los usuarios del sistema
    // Solo accesible para SUPER_ADMIN y ADMIN_CENTRAL
    public List<UsuarioResponseDTO> listar() {
        return userRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // Obtiene el perfil del usuario actualmente logueado
    // Lee el email del JWT via SecurityContextHolder
    public UsuarioResponseDTO obtenerPerfil() {
        UserEntity user = userRepository.findByEmail(getEmailUsuarioActual())
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado"));
        return toDTO(user);
    }

    // Actualiza la foto de perfil del usuario logueado con una URL
    // Es lo único que el usuario puede modificar de su propio perfil
    @Transactional
    public UsuarioResponseDTO actualizarPerfil(UpdatePerfilRequestDTO request) {
        UserEntity user = userRepository.findByEmail(getEmailUsuarioActual())
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado"));
        user.setFotoUrl(request.getFotoUrl());
        return toDTO(userRepository.save(user));
    }

    // Sube o reemplaza la foto de perfil del usuario logueado.
    // El archivo se convierte a data URI base64 y se guarda en foto_url (LONGTEXT).
    // Misma lógica que AulaService.subirImagen — usa el mismo utilitario estático.
    @Transactional
    public UsuarioResponseDTO subirFotoPerfil(MultipartFile file) {
        UserEntity user = userRepository.findByEmail(getEmailUsuarioActual())
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado"));
        String dataUri = AulaService.convertirImagenADataUri(file);
        user.setFotoUrl(dataUri);
        return toDTO(userRepository.save(user));
    }

    // ── Gestión de usuarios (SUPER_ADMIN / ADMIN_CENTRAL) ───────────────────────

    // Actualiza el nombre de un usuario — accesible solo para admins
    @Transactional
    public UsuarioResponseDTO actualizarUsuario(Long id, UpdateUserRequestDTO request) {
        UserEntity user = buscarUsuarioOLanzar(id);
        user.setNombre(request.getNombre());
        actividadLogService.registrarAccionActual(
                "ACTUALIZAR_USUARIO", "usuario", id,
                "Nombre actualizado: " + user.getNombre() + " (" + user.getEmail() + ")");
        return toDTO(userRepository.save(user));
    }

    // Desactiva un usuario — soft delete, no elimina el registro
    @Transactional
    public void desactivar(Long id) {
        UserEntity user = buscarUsuarioOLanzar(id);
        if (!user.isActivo()) throw new OperacionNoPermitidaException("El usuario ya está inactivo");
        user.setActivo(false);
        userRepository.save(user);
        actividadLogService.registrarAccionActual(
                "DESACTIVAR_USUARIO", "usuario", id,
                "Usuario desactivado: " + user.getNombre() + " (" + user.getEmail() + ")");
    }

    // Reactiva un usuario desactivado
    @Transactional
    public void activar(Long id) {
        UserEntity user = buscarUsuarioOLanzar(id);
        if (user.isActivo()) throw new OperacionNoPermitidaException("El usuario ya está activo");
        user.setActivo(true);
        userRepository.save(user);
        actividadLogService.registrarAccionActual(
                "ACTIVAR_USUARIO", "usuario", id,
                "Usuario activado: " + user.getNombre() + " (" + user.getEmail() + ")");
    }

    // ── Maestros (usuarios con rol PROFESOR) y sus materias ─────────────────────

    // Lista los usuarios que tienen al menos un rol PROFESOR, sin duplicados y ordenados por nombre
    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listarProfesores() {
        return rolRepository.findByTipo(TipoRol.PROFESOR).stream()
                .map(rol -> rol.getUsuario())
                .filter(Objects::nonNull)
                // dedupe por id conservando el orden de aparición
                .collect(Collectors.toMap(UserEntity::getId, u -> u, (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(
                        u -> u.getNombre() == null ? "" : u.getNombre(), String.CASE_INSENSITIVE_ORDER))
                .map(this::toDTO)
                .toList();
    }

    // Devuelve las materias actualmente asignadas a un maestro
    @Transactional(readOnly = true)
    public List<MateriaResponseDTO> obtenerMaterias(Long usuarioId) {
        UserEntity usuario = buscarUsuarioOLanzar(usuarioId);
        return usuario.getMaterias().stream()
                .map(this::toMateriaDTO)
                .toList();
    }

    // Reemplaza por completo el conjunto de materias del maestro por la lista dada
    @Transactional
    public List<MateriaResponseDTO> asignarMaterias(Long usuarioId, List<Long> materiaIds) {
        UserEntity usuario = buscarUsuarioOLanzar(usuarioId);

        List<MateriaEntity> materias = (materiaIds == null || materiaIds.isEmpty())
                ? new ArrayList<>()
                : materiaRepository.findAllById(materiaIds);

        usuario.getMaterias().clear();
        usuario.getMaterias().addAll(materias);
        userRepository.save(usuario);

        actividadLogService.registrarAccionActual(
                "ASIGNAR_MATERIAS", "usuario", usuario.getId(),
                "Asignadas " + materias.size() + " materia(s) a " + usuario.getNombre());

        return materias.stream().map(this::toMateriaDTO).toList();
    }

    // ── Asignaciones (grupo, materia) de un maestro ─────────────────────────────

    // Devuelve las asignaciones (grupo, materia) del usuario AUTENTICADO.
    // Usado en la reserva: el profe solo ve los grupos y materias que él imparte.
    @Transactional(readOnly = true)
    public List<AsignacionMaestroResponseDTO> obtenerMisAsignaciones() {
        UserEntity usuario = userRepository.findByEmail(getEmailUsuarioActual())
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado"));
        return asignacionRepository.findByUsuario_Id(usuario.getId()).stream()
                .map(this::toAsignacionDTO)
                .toList();
    }

    // Devuelve las asignaciones (grupo, materia) actuales del maestro
    @Transactional(readOnly = true)
    public List<AsignacionMaestroResponseDTO> obtenerAsignaciones(Long usuarioId) {
        buscarUsuarioOLanzar(usuarioId); // valida que exista
        return asignacionRepository.findByUsuario_Id(usuarioId).stream()
                .map(this::toAsignacionDTO)
                .toList();
    }

    // Reemplaza por completo las asignaciones (grupo, materia) del maestro.
    // Valida que la materia se imparta en el grupo (exista en grupo_materias); si no, rechaza.
    @Transactional
    public List<AsignacionMaestroResponseDTO> asignarGrupoMaterias(
            Long usuarioId, List<AsignarGrupoMateriasRequestDTO.Item> items) {

        UserEntity usuario = buscarUsuarioOLanzar(usuarioId);
        asignacionRepository.deleteByUsuario_Id(usuarioId);

        // Dedupe de pares (grupo, materia) para no chocar con la restricción única.
        Set<String> vistos = new LinkedHashSet<>();
        List<AsignacionMaestroEntity> nuevas = new ArrayList<>();

        for (AsignarGrupoMateriasRequestDTO.Item item : (items == null ? List.<AsignarGrupoMateriasRequestDTO.Item>of() : items)) {
            if (!vistos.add(item.getGrupoId() + "-" + item.getMateriaId())) continue;

            GrupoEntity grupo = grupoRepository.findById(item.getGrupoId())
                    .orElseThrow(() -> new GrupoNoEncontradoException(
                            "Grupo con id " + item.getGrupoId() + " no encontrado"));
            MateriaEntity materia = materiaRepository.findById(item.getMateriaId())
                    .orElseThrow(() -> new MateriaNoEncontradaException(
                            "Materia con id " + item.getMateriaId() + " no encontrada"));

            // Regla: la materia debe impartirse en el grupo
            boolean seImparte = grupo.getMaterias().stream()
                    .anyMatch(m -> m.getId().equals(materia.getId()));
            if (!seImparte) {
                throw new OperacionNoPermitidaException(
                        "La materia \"" + materia.getNombre() + "\" no se imparte en el grupo "
                                + grupo.getNombre() + ", no se puede asignar");
            }

            nuevas.add(AsignacionMaestroEntity.builder()
                    .usuario(usuario)
                    .grupo(grupo)
                    .materia(materia)
                    .build());
        }

        asignacionRepository.saveAll(nuevas);

        actividadLogService.registrarAccionActual(
                "ASIGNAR_GRUPO_MATERIAS", "usuario", usuario.getId(),
                "Asignadas " + nuevas.size() + " (grupo, materia) a " + usuario.getNombre());

        return nuevas.stream().map(this::toAsignacionDTO).toList();
    }

    // Método privado — obtiene el email del usuario autenticado desde el token JWT
    private String getEmailUsuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private UserEntity buscarUsuarioOLanzar(Long usuarioId) {
        return userRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException(
                        "Usuario con id " + usuarioId + " no encontrado"));
    }

    private MateriaResponseDTO toMateriaDTO(MateriaEntity m) {
        return new MateriaResponseDTO(m.getId(), m.getFacultad().getId(), m.getNombre(), m.isActiva());
    }

    private AsignacionMaestroResponseDTO toAsignacionDTO(AsignacionMaestroEntity a) {
        return new AsignacionMaestroResponseDTO(
                a.getId(),
                a.getGrupo().getId(),
                a.getGrupo().getNombre(),
                a.getGrupo().getTurno(),
                a.getMateria().getId(),
                a.getMateria().getNombre());
    }

    // Método privado — convierte UserEntity a UsuarioResponseDTO
    private UsuarioResponseDTO toDTO(UserEntity user) {
        return new UsuarioResponseDTO(
                user.getId(),
                user.getEmail(),
                user.getNombre(),
                user.getFotoUrl(),
                user.isActivo(),
                user.getCreatedAt()
        );
    }
}
