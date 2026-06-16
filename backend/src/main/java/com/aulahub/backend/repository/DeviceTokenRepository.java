package com.aulahub.backend.repository;

import com.aulahub.backend.model.DeviceTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceTokenEntity, Long> {

    Optional<DeviceTokenEntity> findByToken(String token);

    List<DeviceTokenEntity> findByUsuarioId(Long usuarioId);

    void deleteByToken(String token);
}
