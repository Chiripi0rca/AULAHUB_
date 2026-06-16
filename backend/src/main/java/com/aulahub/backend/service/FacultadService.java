package com.aulahub.backend.service;

import com.aulahub.backend.dto.request.FacultadRequestDTO;
import com.aulahub.backend.dto.response.FacultadResponseDTO;
import com.aulahub.backend.exception.FacultadNoEncontradaException;
import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.model.FacultadEntity;
import com.aulahub.backend.repository.AulaRepository;
import com.aulahub.backend.repository.FacultadRepository;
import com.aulahub.backend.repository.MateriaRepository;
import com.aulahub.backend.repository.TipoReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FacultadService {

    private final FacultadRepository facultadRepository;
    private final AulaRepository aulaRepository;
    private final MateriaRepository materiaRepository;
    private final TipoReservaRepository tipoReservaRepository;
    private final ActividadLogService actividadLogService;

    // Devuelve la lista de todas las facultades registradas en el sistema
    public List<FacultadResponseDTO> listar() {
        return facultadRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // Busca y devuelve una facultad por su ID
    // Lanza FacultadNoEncontradaException si no existe
    public FacultadResponseDTO obtenerPorId(Long id) {
        return toDTO(buscarOLanzar(id));
    }

    // Crea una nueva facultad
    // Lanza OperacionNoPermitidaException si la clave ya está registrada
    @Transactional
    public FacultadResponseDTO crear(FacultadRequestDTO request) {
        if (facultadRepository.existsByClave(request.getClave())) {
            throw new OperacionNoPermitidaException("Ya existe una facultad con la clave " + request.getClave());
        }
        FacultadEntity nueva = FacultadEntity.builder()
                .clave(request.getClave())
                .nombre(request.getNombre())
                .build();
        FacultadEntity guardada = facultadRepository.save(nueva);
        actividadLogService.registrarAccionActual(
                "CREAR_FACULTAD", "facultad", guardada.getId(),
                "Facultad creada: " + guardada.getNombre() + " (" + guardada.getClave() + ")");
        return toDTO(guardada);
    }

    // Actualiza el nombre y/o clave de una facultad existente
    // Lanza OperacionNoPermitidaException si la nueva clave ya la usa otra facultad
    @Transactional
    public FacultadResponseDTO actualizar(Long id, FacultadRequestDTO request) {
        FacultadEntity facultad = buscarOLanzar(id);

        if (facultadRepository.existsByClaveAndIdNot(request.getClave(), id)) {
            throw new OperacionNoPermitidaException("Ya existe una facultad con la clave " + request.getClave());
        }
        facultad.setClave(request.getClave());
        facultad.setNombre(request.getNombre());
        FacultadEntity actualizada = facultadRepository.save(facultad);
        actividadLogService.registrarAccionActual(
                "ACTUALIZAR_FACULTAD", "facultad", actualizada.getId(),
                "Facultad actualizada: " + actualizada.getNombre() + " (" + actualizada.getClave() + ")");
        return toDTO(actualizada);
    }

    // Pone activa = false a la facultad y hace cascade sobre aulas, materias y tipos de reserva
    @Transactional
    public void desactivar(Long id) {
        FacultadEntity facultad = buscarOLanzar(id);
        facultad.setActiva(false);
        facultadRepository.save(facultad);
        aulaRepository.desactivarByFacultadId(id);
        materiaRepository.desactivarByFacultadId(id);
        tipoReservaRepository.desactivarByFacultadId(id);
        actividadLogService.registrarAccionActual(
                "DESACTIVAR_FACULTAD", "facultad", id,
                "Facultad desactivada: " + facultad.getNombre() + " (" + facultad.getClave() + ")");
    }

    // Reactiva la facultad — las aulas/materias/tipos_reserva permanecen como estaban
    @Transactional
    public void activar(Long id) {
        FacultadEntity facultad = buscarOLanzar(id);
        facultad.setActiva(true);
        facultadRepository.save(facultad);
        actividadLogService.registrarAccionActual(
                "ACTIVAR_FACULTAD", "facultad", id,
                "Facultad activada: " + facultad.getNombre() + " (" + facultad.getClave() + ")");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private FacultadEntity buscarOLanzar(Long id) {
        return facultadRepository.findById(id)
                .orElseThrow(() -> new FacultadNoEncontradaException("Facultad con id " + id + " no encontrada"));
    }

    private FacultadResponseDTO toDTO(FacultadEntity f) {
        return new FacultadResponseDTO(f.getId(), f.getClave(), f.getNombre(), f.isActiva());
    }
}
