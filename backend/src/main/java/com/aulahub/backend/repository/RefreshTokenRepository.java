package com.aulahub.backend.repository;

import com.aulahub.backend.model.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    // Busca un refresh token por su valor — usado en refresh y logout
    Optional<RefreshTokenEntity> findByToken(String token);

    // Elimina el refresh token por su valor — usado en logout
    void deleteByToken(String token);

    // Elimina todos los refresh tokens de un usuario — usado al cambiar o resetear
    // la password para forzar re-login en todos los dispositivos
    void deleteByUser_Id(Long userId);

    // Tokens de un usuario del mas nuevo al mas viejo — usado para limitar cuantas
    // sesiones abiertas puede acumular un usuario
    List<RefreshTokenEntity> findByUser_IdOrderByExpiryDateDesc(Long userId);

    // Elimina todos los tokens cuya fecha de expiración ya pasó — usado por el scheduler de limpieza
    void deleteAllByExpiryDateBefore(LocalDateTime fechaLimite);
}
