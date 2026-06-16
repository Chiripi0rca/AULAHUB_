package com.aulahub.backend.scheduler;

import com.aulahub.backend.repository.RefreshTokenRepository;
import com.aulahub.backend.service.ReservaService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ReservaScheduler {

    private final ReservaService reservaService;
    private final RefreshTokenRepository refreshTokenRepository;

    // Se ejecuta todos los días a medianoche (00:00:00)
    // Busca reservas PENDIENTES cuya fecha ya pasó y las rechaza automáticamente
    // Motivo: "Rechazada automáticamente por el sistema"
    // zone fija la hora LOCAL de Culiacan aunque el reloj del servidor este en UTC
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Mazatlan")
    public void rechazarReservasVencidas() {
        reservaService.rechazarVencidas();
    }

    // Se ejecuta todos los días a las 2:00am
    // Elimina los refresh tokens cuya fecha de expiración ya pasó
    // Evita que la tabla refresh_tokens crezca indefinidamente
    @Scheduled(cron = "0 0 2 * * *", zone = "America/Mazatlan")
    @Transactional
    public void limpiarRefreshTokensExpirados() {
        refreshTokenRepository.deleteAllByExpiryDateBefore(LocalDateTime.now());
    }
}
