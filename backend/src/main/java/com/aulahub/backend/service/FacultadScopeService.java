package com.aulahub.backend.service;

import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.model.RolEntity;
import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.repository.RolRepository;
import com.aulahub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

// Scope por facultad (RLS) del SUPER_ADMIN autenticado, centralizado en un solo lugar.
// Antes cada service duplicaba estos helpers a mano; un endpoint de listado nuevo que
// olvidara replicarlos podia filtrar datos de otras facultades.
@Service
@RequiredArgsConstructor
public class FacultadScopeService {

    private final UserRepository userRepository;
    private final RolRepository rolRepository;

    // Devuelve el ID de facultad del SUPER_ADMIN autenticado, o null si el usuario
    // actual no es SUPER_ADMIN. Los listados lo usan para filtrar por facultad (RLS).
    public Long getFacultadIdSuperAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .map(u -> rolRepository.findByUsuarioId(u.getId()).stream()
                        .filter(r -> r.getTipo() == TipoRol.SUPER_ADMIN)
                        .findFirst()
                        .map(RolEntity::getFacultad)
                        .map(f -> f != null ? f.getId() : null)
                        .orElse(null))
                .orElse(null);
    }

    // Si el usuario es SUPER_ADMIN, valida que la facultad solicitada sea la suya.
    // Para los demas roles no aplica restriccion aqui (la autorizacion por rol ya
    // la hace @PreAuthorize en el controller).
    public void validarEscopePropia(Long facultadIdSolicitada) {
        Long miFacultad = getFacultadIdSuperAdmin();
        if (miFacultad != null && !miFacultad.equals(facultadIdSolicitada)) {
            throw new OperacionNoPermitidaException(
                    "Solo puedes gestionar recursos de tu propia facultad");
        }
    }
}
