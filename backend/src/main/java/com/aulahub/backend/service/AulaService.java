package com.aulahub.backend.service;

import com.aulahub.backend.dto.request.AulaRequestDTO;
import com.aulahub.backend.dto.response.AulaResponseDTO;
import com.aulahub.backend.exception.AulaNoEncontradaException;
import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.model.AulaEntity;
import com.aulahub.backend.model.FacultadEntity;
import com.aulahub.backend.exception.FacultadNoEncontradaException;
import com.aulahub.backend.repository.AulaRepository;
import com.aulahub.backend.repository.FacultadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AulaService {

    private final AulaRepository aulaRepository;
    private final FacultadRepository facultadRepository;
    private final ActividadLogService actividadLogService;
    private final FacultadScopeService facultadScope;

    // Devuelve aulas activas. Si el usuario autenticado es SUPER_ADMIN,
    // filtra automáticamente por su facultad (RLS).
    @Transactional(readOnly = true)
    public List<AulaResponseDTO> listar(boolean conImagen) {
        Long facultadId = facultadScope.getFacultadIdSuperAdmin();
        if (facultadId != null) return listarPorFacultad(facultadId, conImagen);
        return aulaRepository.findByActivaTrue()
                .stream()
                .map(a -> toDTO(a, conImagen))
                .toList();
    }

    // Devuelve todas las aulas activas de una facultad específica
    @Transactional(readOnly = true)
    public List<AulaResponseDTO> listarPorFacultad(Long facultadId, boolean conImagen) {
        return aulaRepository.findByFacultadIdAndActivaTrue(facultadId)
                .stream()
                .map(a -> toDTO(a, conImagen))
                .toList();
    }

    // Busca y devuelve un aula por su ID
    @Transactional(readOnly = true)
    public AulaResponseDTO obtenerPorId(Long id) {
        return toDTO(buscarOLanzar(id));
    }

    // Crea una nueva aula dentro de una facultad
    // Valida que el código no esté duplicado y que SUPER_ADMIN solo cree en su facultad
    @Transactional
    public AulaResponseDTO crear(AulaRequestDTO request) {
        facultadScope.validarEscopePropia(request.getFacultadId());
        FacultadEntity facultad = facultadRepository.findById(request.getFacultadId())
                .orElseThrow(() -> new FacultadNoEncontradaException(
                        "Facultad con id " + request.getFacultadId() + " no encontrada"));

        if (aulaRepository.existsByFacultadIdAndCodigo(facultad.getId(), request.getCodigo())) {
            throw new OperacionNoPermitidaException(
                    "Ya existe el código \"" + request.getCodigo() + "\" en esa facultad");
        }

        AulaEntity nueva = AulaEntity.builder()
                .facultad(facultad)
                .codigo(request.getCodigo())
                .nombre(request.getNombre())
                .capacidad(request.getCapacidad())
                .build();

        AulaEntity guardada = aulaRepository.save(nueva);
        actividadLogService.registrarAccionActual(
                "CREAR_AULA", "aula", guardada.getId(),
                "Aula creada: " + guardada.getCodigo() + " — " + guardada.getNombre()
                        + " en " + facultad.getNombre());

        return toDTO(guardada);
    }

    // Actualiza los datos de un aula existente
    @Transactional
    public AulaResponseDTO actualizar(Long id, AulaRequestDTO request) {
        facultadScope.validarEscopePropia(request.getFacultadId());
        AulaEntity aula = buscarOLanzar(id);
        FacultadEntity facultad = facultadRepository.findById(request.getFacultadId())
                .orElseThrow(() -> new FacultadNoEncontradaException(
                        "Facultad con id " + request.getFacultadId() + " no encontrada"));

        if (aulaRepository.existsByFacultadIdAndCodigoAndIdNot(facultad.getId(), request.getCodigo(), id)) {
            throw new OperacionNoPermitidaException(
                    "Ya existe el código \"" + request.getCodigo() + "\" en esa facultad");
        }

        aula.setFacultad(facultad);
        aula.setCodigo(request.getCodigo());
        aula.setNombre(request.getNombre());
        aula.setCapacidad(request.getCapacidad());

        AulaEntity actualizada = aulaRepository.save(aula);
        actividadLogService.registrarAccionActual(
                "ACTUALIZAR_AULA", "aula", actualizada.getId(),
                "Aula actualizada: " + actualizada.getCodigo() + " — " + actualizada.getNombre());

        return toDTO(actualizada);
    }

    // Pone activa = false al aula (soft delete)
    @Transactional
    public void desactivar(Long id) {
        AulaEntity aula = buscarOLanzar(id);
        aula.setActiva(false);
        aulaRepository.save(aula);
        actividadLogService.registrarAccionActual(
                "DESACTIVAR_AULA", "aula", id,
                "Aula desactivada: " + aula.getCodigo() + " — " + aula.getNombre());
    }

    // Reactiva un aula que fue desactivada
    @Transactional
    public void activar(Long id) {
        AulaEntity aula = buscarOLanzar(id);
        aula.setActiva(true);
        aulaRepository.save(aula);
        actividadLogService.registrarAccionActual(
                "ACTIVAR_AULA", "aula", id,
                "Aula activada: " + aula.getCodigo() + " — " + aula.getNombre());
    }

    // Sube o reemplaza la imagen de un aula.
    // El archivo se convierte a data URI base64 y se guarda en imagen_url (LONGTEXT).
    // Límite: 5 MB. Formatos aceptados: image/jpeg, image/png, image/webp, image/gif.
    @Transactional
    public AulaResponseDTO subirImagen(Long id, MultipartFile file) {
        AulaEntity aula = buscarOLanzar(id);
        String dataUri = convertirImagenADataUri(file);
        aula.setImagenUrl(dataUri);
        AulaEntity guardada = aulaRepository.save(aula);
        actividadLogService.registrarAccionActual(
                "SUBIR_IMAGEN_AULA", "aula", id,
                "Imagen actualizada en aula: " + aula.getCodigo() + " — " + aula.getNombre());
        return toDTO(guardada);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private AulaEntity buscarOLanzar(Long id) {
        return aulaRepository.findById(id)
                .orElseThrow(() -> new AulaNoEncontradaException("Aula con id " + id + " no encontrada"));
    }

    private AulaResponseDTO toDTO(AulaEntity aula) {
        return new AulaResponseDTO(
                aula.getId(),
                aula.getFacultad().getId(),
                aula.getCodigo(),
                aula.getNombre(),
                aula.isActiva(),
                aula.getCapacidad(),
                aula.getImagenUrl()
        );
    }

    // Version para listados: por defecto omite la imagen base64 (conImagen=false)
    // para no enviar todas las fotos en una sola respuesta. Solo la incluye cuando
    // se pide explicitamente (pantallas de gestion que muestran thumbnail por fila).
    // La imagen completa de una sola aula se obtiene siempre con GET /aulas/{id}.
    private AulaResponseDTO toDTO(AulaEntity aula, boolean conImagen) {
        return new AulaResponseDTO(
                aula.getId(),
                aula.getFacultad().getId(),
                aula.getCodigo(),
                aula.getNombre(),
                aula.isActiva(),
                aula.getCapacidad(),
                conImagen ? aula.getImagenUrl() : null
        );
    }

    // Valida y convierte un MultipartFile de imagen a un data URI base64.
    // Reutilizado desde UsuarioService para la foto de perfil.
    static final long MAX_BYTES = 5 * 1024 * 1024L; // 5 MB
    static final Set<String> TIPOS_PERMITIDOS = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    static String convertirImagenADataUri(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new OperacionNoPermitidaException("El archivo de imagen está vacío");
        }
        String contentType = file.getContentType();
        if (contentType == null || !TIPOS_PERMITIDOS.contains(contentType)) {
            throw new OperacionNoPermitidaException(
                    "Formato no permitido. Use: jpg, png, webp o gif");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new OperacionNoPermitidaException(
                    "La imagen supera el límite de 5 MB");
        }
        try {
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            return "data:" + contentType + ";base64," + base64;
        } catch (IOException e) {
            throw new OperacionNoPermitidaException(
                    "No se pudo leer el archivo: " + e.getMessage());
        }
    }
}
