package com.aulahub.backend.service;

import com.aulahub.backend.dto.request.GrupoRequestDTO;
import com.aulahub.backend.dto.response.GrupoResponseDTO;
import com.aulahub.backend.dto.response.MateriaResponseDTO;
import com.aulahub.backend.exception.FacultadNoEncontradaException;
import com.aulahub.backend.exception.GrupoNoEncontradoException;
import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.model.FacultadEntity;
import com.aulahub.backend.model.GrupoEntity;
import com.aulahub.backend.model.MateriaEntity;
import com.aulahub.backend.model.enums.Turno;
import com.aulahub.backend.repository.FacultadRepository;
import com.aulahub.backend.repository.GrupoRepository;
import com.aulahub.backend.repository.MateriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GrupoService {

    private final GrupoRepository grupoRepository;
    private final FacultadRepository facultadRepository;
    private final MateriaRepository materiaRepository;
    private final ActividadLogService actividadLogService;
    private final FacultadScopeService facultadScope;

    // Todos los grupos del sistema
    @Transactional(readOnly = true)
    public List<GrupoResponseDTO> listar() {
        return grupoRepository.findAll().stream().map(this::toDTO).toList();
    }

    // Grupos activos de una facultad
    @Transactional(readOnly = true)
    public List<GrupoResponseDTO> listarPorFacultad(Long facultadId) {
        return grupoRepository.findByFacultad_IdAndActivoTrue(facultadId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public GrupoResponseDTO obtenerPorId(Long id) {
        return toDTO(buscarOLanzar(id));
    }

    // Crea un grupo en una facultad — la combinación facultad+nombre es única
    @Transactional
    public GrupoResponseDTO crear(GrupoRequestDTO request) {
        facultadScope.validarEscopePropia(request.getFacultadId());
        FacultadEntity facultad = buscarFacultadOLanzar(request.getFacultadId());
        if (grupoRepository.existsByFacultad_IdAndNombre(facultad.getId(), request.getNombre())) {
            throw new OperacionNoPermitidaException(
                    "Ya existe el grupo " + request.getNombre() + " en esa facultad");
        }
        GrupoEntity nuevo = GrupoEntity.builder()
                .facultad(facultad)
                .nombre(request.getNombre())
                .turno(turnoOrDefault(request.getTurno()))
                .build();
        GrupoEntity guardado = grupoRepository.save(nuevo);
        actividadLogService.registrarAccionActual(
                "CREAR_GRUPO", "grupo", guardado.getId(),
                "Grupo creado: " + guardado.getNombre() + " en " + facultad.getNombre());
        return toDTO(guardado);
    }

    @Transactional
    public GrupoResponseDTO actualizar(Long id, GrupoRequestDTO request) {
        facultadScope.validarEscopePropia(request.getFacultadId());
        GrupoEntity grupo = buscarOLanzar(id);
        FacultadEntity facultad = buscarFacultadOLanzar(request.getFacultadId());
        if (grupoRepository.existsByFacultad_IdAndNombreAndIdNot(facultad.getId(), request.getNombre(), id)) {
            throw new OperacionNoPermitidaException(
                    "Ya existe el grupo " + request.getNombre() + " en esa facultad");
        }
        grupo.setFacultad(facultad);
        grupo.setNombre(request.getNombre());
        grupo.setTurno(turnoOrDefault(request.getTurno()));
        GrupoEntity actualizado = grupoRepository.save(grupo);
        actividadLogService.registrarAccionActual(
                "ACTUALIZAR_GRUPO", "grupo", actualizado.getId(),
                "Grupo actualizado: " + actualizado.getNombre());
        return toDTO(actualizado);
    }

    @Transactional
    public void desactivar(Long id) {
        GrupoEntity grupo = buscarOLanzar(id);
        grupo.setActivo(false);
        grupoRepository.save(grupo);
        actividadLogService.registrarAccionActual(
                "DESACTIVAR_GRUPO", "grupo", id, "Grupo desactivado: " + grupo.getNombre());
    }

    @Transactional
    public void activar(Long id) {
        GrupoEntity grupo = buscarOLanzar(id);
        grupo.setActivo(true);
        grupoRepository.save(grupo);
        actividadLogService.registrarAccionActual(
                "ACTIVAR_GRUPO", "grupo", id, "Grupo activado: " + grupo.getNombre());
    }

    // ── Materias que se imparten en el grupo ───────────────────────────────────

    @Transactional(readOnly = true)
    public List<MateriaResponseDTO> listarMaterias(Long grupoId) {
        GrupoEntity grupo = buscarOLanzar(grupoId);
        return grupo.getMaterias().stream().map(this::toMateriaDTO).toList();
    }

    // Reemplaza por completo el conjunto de materias que se imparten en el grupo
    @Transactional
    public List<MateriaResponseDTO> asignarMaterias(Long grupoId, List<Long> materiaIds) {
        GrupoEntity grupo = buscarOLanzar(grupoId);
        List<MateriaEntity> materias = (materiaIds == null || materiaIds.isEmpty())
                ? new ArrayList<>()
                : materiaRepository.findAllById(materiaIds);

        grupo.getMaterias().clear();
        grupo.getMaterias().addAll(materias);
        grupoRepository.save(grupo);

        actividadLogService.registrarAccionActual(
                "ASIGNAR_MATERIAS_GRUPO", "grupo", grupo.getId(),
                "El grupo " + grupo.getNombre() + " ahora imparte " + materias.size() + " materia(s)");

        return materias.stream().map(this::toMateriaDTO).toList();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private GrupoEntity buscarOLanzar(Long id) {
        return grupoRepository.findById(id)
                .orElseThrow(() -> new GrupoNoEncontradoException("Grupo con id " + id + " no encontrado"));
    }

    private FacultadEntity buscarFacultadOLanzar(Long facultadId) {
        return facultadRepository.findById(facultadId)
                .orElseThrow(() -> new FacultadNoEncontradaException(
                        "Facultad con id " + facultadId + " no encontrada"));
    }

    // Un grupo siempre tiene turno definido; AMBOS no aplica (eso es solo de ADMIN).
    // Si el request no trae turno, se usa MATUTINO por defecto.
    private Turno turnoOrDefault(Turno turno) {
        return (turno == null || turno == Turno.AMBOS) ? Turno.MATUTINO : turno;
    }

    private GrupoResponseDTO toDTO(GrupoEntity g) {
        return new GrupoResponseDTO(g.getId(), g.getFacultad().getId(), g.getNombre(), g.isActivo(), g.getTurno());
    }

    private MateriaResponseDTO toMateriaDTO(MateriaEntity m) {
        return new MateriaResponseDTO(m.getId(), m.getFacultad().getId(), m.getNombre(), m.isActiva());
    }
}
