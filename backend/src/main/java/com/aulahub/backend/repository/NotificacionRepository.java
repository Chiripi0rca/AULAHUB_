package com.aulahub.backend.repository;

import com.aulahub.backend.model.NotificacionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<NotificacionEntity, Long> {

    // Todas las notificaciones de un usuario, más recientes primero — sin paginar (uso interno)
    List<NotificacionEntity> findByUsuario_IdOrderByCreatedAtDesc(Long usuarioId);

    // Versión paginada — para el endpoint público
    Page<NotificacionEntity> findByUsuario_IdOrderByCreatedAtDesc(Long usuarioId, Pageable pageable);

    // Conteo de no leídas — usado para el badge de la campana en el frontend
    long countByUsuario_IdAndLeidoFalse(Long usuarioId);

    // Marca todas las notificaciones de un usuario como leídas en un solo UPDATE
    @Modifying
    @Query("UPDATE NotificacionEntity n SET n.leido = true WHERE n.usuario.id = :usuarioId AND n.leido = false")
    void marcarTodasLeidasByUsuarioId(@Param("usuarioId") Long usuarioId);
}
