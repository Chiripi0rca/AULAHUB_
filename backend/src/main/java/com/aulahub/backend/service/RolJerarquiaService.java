package com.aulahub.backend.service;

import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.repository.RolRepository;
import com.aulahub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Jerarquía de roles: PROFESOR < ADMIN < SUPER_ADMIN < ADMIN_CENTRAL.
// Centraliza el cálculo del "rango" de un usuario para decidir qué tipos de reserva
// puede usar y si sus reservas se auto-aprueban.
@Service
@RequiredArgsConstructor
public class RolJerarquiaService {

    private final RolRepository rolRepository;
    private final UserRepository userRepository;

    // Rango numérico de un rol — a mayor número, más privilegios
    public int rango(TipoRol tipo) {
        if (tipo == null) return 0;
        return switch (tipo) {
            case PROFESOR -> 0;
            case ADMIN -> 1;
            case SUPER_ADMIN -> 2;
            case ADMIN_CENTRAL -> 3;
        };
    }

    // Rango más alto entre todos los roles asignados a un usuario (PROFESOR=0 si no tiene)
    @Transactional(readOnly = true)
    public int rangoMaximoUsuario(Long usuarioId) {
        return rolRepository.findByUsuarioId(usuarioId).stream()
                .map(r -> rango(r.getTipo()))
                .max(Integer::compareTo)
                .orElse(0);
    }

    // Rango del usuario autenticado (según el email del JWT)
    @Transactional(readOnly = true)
    public int rangoMaximoActual() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .map(u -> rangoMaximoUsuario(u.getId()))
                .orElse(0);
    }

    // true si el rango corresponde a ADMIN o superior (sus reservas se auto-aprueban)
    public boolean esAdminOSuperior(int rango) {
        return rango >= rango(TipoRol.ADMIN);
    }
}
