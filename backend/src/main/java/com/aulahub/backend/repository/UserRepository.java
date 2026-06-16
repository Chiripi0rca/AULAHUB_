package com.aulahub.backend.repository;

import com.aulahub.backend.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    // Busca usuario por email — usado en login y UserDetailServiceImpl
    Optional<UserEntity> findByEmail(String email);

    // Verifica si ya existe un usuario con ese email — usado en registrar para evitar duplicados
    boolean existsByEmail(String email);

    // Contador de usuarios activos en todo el sistema — para DashboardService (ADMIN_CENTRAL)
    long countByActivoTrue();
}
