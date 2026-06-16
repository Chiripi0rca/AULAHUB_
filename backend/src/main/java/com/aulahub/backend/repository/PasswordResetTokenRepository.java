package com.aulahub.backend.repository;

import com.aulahub.backend.model.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    // Busca un token por su valor — usado en resetPassword para validarlo
    Optional<PasswordResetTokenEntity> findByToken(String token);

    // Elimina el token anterior de un usuario antes de crear uno nuevo
    void deleteByUser_Id(Long userId);
}
