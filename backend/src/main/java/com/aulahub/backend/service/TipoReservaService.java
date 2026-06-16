package com.aulahub.backend.service;

import com.aulahub.backend.dto.request.TipoReservaRequestDTO;
import com.aulahub.backend.dto.response.TipoReservaResponseDTO;
import com.aulahub.backend.exception.FacultadNoEncontradaException;
import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.exception.TipoReservaNoEncontradoException;
import com.aulahub.backend.model.FacultadEntity;
import com.aulahub.backend.model.TipoReservaEntity;
import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.repository.FacultadRepository;
import com.aulahub.backend.repository.TipoReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TipoReservaService {

    private final TipoReservaRepository tipoReservaRepository;
    private final FacultadRepository facultadRepository;
    private final ActividadLogService actividadLogService;
    private final RolJerarquiaService rolJerarquia;

    // Devuelve la lista de todos los tipos de reserva registrados en el sistema
    @Transactional(readOnly = true)
    public List<TipoReservaResponseDTO> listar() {
        return tipoReservaRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // Devuelve todos los tipos de reserva activos de una facultad específica
    @Transactional(readOnly = true)
    public List<TipoReservaResponseDTO> listarPorFacultad(Long facultadId) {
        return tipoReservaRepository.findByFacultad_IdAndActivoTrue(facultadId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // Tipos de reserva activos que el usuario autenticado puede usar según su rol
    // (rol_minimo <= rango del usuario). Alimenta el dropdown de la pantalla de reserva.
    @Transactional(readOnly = true)
    public List<TipoReservaResponseDTO> listarDisponibles() {
        int rangoUsuario = rolJerarquia.rangoMaximoActual();
        return tipoReservaRepository.findAll().stream()
                .filter(TipoReservaEntity::isActivo)
                .filter(t -> rolJerarquia.rango(t.getRolMinimo()) <= rangoUsuario)
                .map(this::toDTO)
                .toList();
    }

    // Busca y devuelve un tipo de reserva por su ID
    // Lanza TipoReservaNoEncontradoException si no existe
    @Transactional(readOnly = true)
    public TipoReservaResponseDTO obtenerPorId(Long id) {
        return toDTO(buscarOLanzar(id));
    }

    // Crea un nuevo tipo de reserva para una facultad
    // Lanza OperacionNoPermitidaException si la clave ya existe dentro de esa facultad
    @Transactional
    public TipoReservaResponseDTO crear(TipoReservaRequestDTO request) {
        FacultadEntity facultad = buscarFacultadOLanzar(request.getFacultadId());

        if (tipoReservaRepository.existsByFacultad_IdAndClave(facultad.getId(), request.getClave())) {
            throw new OperacionNoPermitidaException(
                    "Ya existe un tipo de reserva con la clave " + request.getClave() + " en esa facultad");
        }

        TipoReservaEntity nuevo = TipoReservaEntity.builder()
                .facultad(facultad)
                .clave(request.getClave())
                .nombre(request.getNombre())
                .rolMinimo(request.getRolMinimo() != null ? request.getRolMinimo() : TipoRol.PROFESOR)
                .prioridad(request.getPrioridad() != null ? request.getPrioridad() : 0)
                .build();
        TipoReservaEntity guardado = tipoReservaRepository.save(nuevo);

        actividadLogService.registrarAccionActual(
                "CREAR_TIPO_RESERVA", "tipo_reserva", guardado.getId(),
                "Tipo de reserva creado: " + guardado.getNombre() + " (" + guardado.getClave() + ")");
        return toDTO(guardado);
    }

    // Actualiza la clave y/o el nombre de un tipo de reserva existente
    // Lanza OperacionNoPermitidaException si la nueva clave ya la usa otro tipo en la misma facultad
    @Transactional
    public TipoReservaResponseDTO actualizar(Long id, TipoReservaRequestDTO request) {
        TipoReservaEntity tipo = buscarOLanzar(id);
        FacultadEntity facultad = buscarFacultadOLanzar(request.getFacultadId());

        if (tipoReservaRepository.existsByFacultad_IdAndClaveAndIdNot(facultad.getId(), request.getClave(), id)) {
            throw new OperacionNoPermitidaException(
                    "Ya existe un tipo de reserva con la clave " + request.getClave() + " en esa facultad");
        }

        tipo.setFacultad(facultad);
        tipo.setClave(request.getClave());
        tipo.setNombre(request.getNombre());
        if (request.getRolMinimo() != null) tipo.setRolMinimo(request.getRolMinimo());
        if (request.getPrioridad() != null) tipo.setPrioridad(request.getPrioridad());
        TipoReservaEntity actualizado = tipoReservaRepository.save(tipo);

        actividadLogService.registrarAccionActual(
                "ACTUALIZAR_TIPO_RESERVA", "tipo_reserva", actualizado.getId(),
                "Tipo de reserva actualizado: " + actualizado.getNombre() + " (" + actualizado.getClave() + ")");
        return toDTO(actualizado);
    }

    // Pone activo = false al tipo de reserva — ya no podrá usarse para crear nuevas reservas
    @Transactional
    public void desactivar(Long id) {
        TipoReservaEntity tipo = buscarOLanzar(id);
        tipo.setActivo(false);
        tipoReservaRepository.save(tipo);
        actividadLogService.registrarAccionActual(
                "DESACTIVAR_TIPO_RESERVA", "tipo_reserva", id,
                "Tipo de reserva desactivado: " + tipo.getNombre() + " (" + tipo.getClave() + ")");
    }

    // Reactiva un tipo de reserva que fue desactivado, pone activo = true
    @Transactional
    public void activar(Long id) {
        TipoReservaEntity tipo = buscarOLanzar(id);
        tipo.setActivo(true);
        tipoReservaRepository.save(tipo);
        actividadLogService.registrarAccionActual(
                "ACTIVAR_TIPO_RESERVA", "tipo_reserva", id,
                "Tipo de reserva activado: " + tipo.getNombre() + " (" + tipo.getClave() + ")");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TipoReservaEntity buscarOLanzar(Long id) {
        return tipoReservaRepository.findById(id)
                .orElseThrow(() -> new TipoReservaNoEncontradoException(
                        "Tipo de reserva con id " + id + " no encontrado"));
    }

    private FacultadEntity buscarFacultadOLanzar(Long facultadId) {
        return facultadRepository.findById(facultadId)
                .orElseThrow(() -> new FacultadNoEncontradaException(
                        "Facultad con id " + facultadId + " no encontrada"));
    }

    private TipoReservaResponseDTO toDTO(TipoReservaEntity t) {
        return new TipoReservaResponseDTO(
                t.getId(), t.getFacultad().getId(), t.getClave(), t.getNombre(), t.isActivo(),
                t.getRolMinimo(), t.getPrioridad());
    }
}
