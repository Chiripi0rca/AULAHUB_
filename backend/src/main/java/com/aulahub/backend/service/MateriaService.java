package com.aulahub.backend.service;

import com.aulahub.backend.dto.request.MateriaRequestDTO;
import com.aulahub.backend.dto.response.MateriaResponseDTO;
import com.aulahub.backend.exception.FacultadNoEncontradaException;
import com.aulahub.backend.exception.MateriaNoEncontradaException;
import com.aulahub.backend.model.FacultadEntity;
import com.aulahub.backend.model.MateriaEntity;
import com.aulahub.backend.repository.FacultadRepository;
import com.aulahub.backend.repository.MateriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MateriaService {

    private final MateriaRepository materiaRepository;
    private final FacultadRepository facultadRepository;
    private final ActividadLogService actividadLogService;
    private final FacultadScopeService facultadScope;

    // Devuelve materias. Si el usuario es SUPER_ADMIN aplica RLS automático por facultad.
    @Transactional(readOnly = true)
    public List<MateriaResponseDTO> listar() {
        Long facultadId = facultadScope.getFacultadIdSuperAdmin();
        if (facultadId != null) return listarPorFacultad(facultadId);
        return materiaRepository.findAll().stream().map(this::toDTO).toList();
    }

    // Devuelve todas las materias activas de una facultad específica
    @Transactional(readOnly = true)
    public List<MateriaResponseDTO> listarPorFacultad(Long facultadId) {
        return materiaRepository.findByFacultad_IdAndActivaTrue(facultadId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // Busca y devuelve una materia por su ID
    // Lanza MateriaNoEncontradaException si no existe
    @Transactional(readOnly = true)
    public MateriaResponseDTO obtenerPorId(Long id) {
        return toDTO(buscarOLanzar(id));
    }

    // Crea una nueva materia dentro de una facultad
    @Transactional
    public MateriaResponseDTO crear(MateriaRequestDTO request) {
        facultadScope.validarEscopePropia(request.getFacultadId());
        FacultadEntity facultad = buscarFacultadOLanzar(request.getFacultadId());

        MateriaEntity nueva = MateriaEntity.builder()
                .facultad(facultad)
                .nombre(request.getNombre())
                .build();
        MateriaEntity guardada = materiaRepository.save(nueva);

        actividadLogService.registrarAccionActual(
                "CREAR_MATERIA", "materia", guardada.getId(),
                "Materia creada: " + guardada.getNombre() + " en " + facultad.getNombre());
        return toDTO(guardada);
    }

    // Actualiza el nombre y/o la facultad de una materia existente
    @Transactional
    public MateriaResponseDTO actualizar(Long id, MateriaRequestDTO request) {
        facultadScope.validarEscopePropia(request.getFacultadId());
        MateriaEntity materia = buscarOLanzar(id);
        FacultadEntity facultad = buscarFacultadOLanzar(request.getFacultadId());

        materia.setFacultad(facultad);
        materia.setNombre(request.getNombre());
        MateriaEntity actualizada = materiaRepository.save(materia);

        actividadLogService.registrarAccionActual(
                "ACTUALIZAR_MATERIA", "materia", actualizada.getId(),
                "Materia actualizada: " + actualizada.getNombre());
        return toDTO(actualizada);
    }

    // Pone activa = false a la materia (soft delete)
    @Transactional
    public void desactivar(Long id) {
        MateriaEntity materia = buscarOLanzar(id);
        materia.setActiva(false);
        materiaRepository.save(materia);
        actividadLogService.registrarAccionActual(
                "DESACTIVAR_MATERIA", "materia", id,
                "Materia desactivada: " + materia.getNombre());
    }

    // Reactiva una materia que fue desactivada, pone activa = true
    @Transactional
    public void activar(Long id) {
        MateriaEntity materia = buscarOLanzar(id);
        materia.setActiva(true);
        materiaRepository.save(materia);
        actividadLogService.registrarAccionActual(
                "ACTIVAR_MATERIA", "materia", id,
                "Materia activada: " + materia.getNombre());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MateriaEntity buscarOLanzar(Long id) {
        return materiaRepository.findById(id)
                .orElseThrow(() -> new MateriaNoEncontradaException("Materia con id " + id + " no encontrada"));
    }

    private FacultadEntity buscarFacultadOLanzar(Long facultadId) {
        return facultadRepository.findById(facultadId)
                .orElseThrow(() -> new FacultadNoEncontradaException(
                        "Facultad con id " + facultadId + " no encontrada"));
    }

    private MateriaResponseDTO toDTO(MateriaEntity m) {
        return new MateriaResponseDTO(m.getId(), m.getFacultad().getId(), m.getNombre(), m.isActiva());
    }

}
