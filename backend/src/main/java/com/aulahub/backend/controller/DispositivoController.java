package com.aulahub.backend.controller;

import com.aulahub.backend.dto.request.TokenRequestDTO;
import com.aulahub.backend.service.PushService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Registro/baja del token de push del dispositivo del usuario autenticado.
@RestController
@RequestMapping("/api/dispositivos")
@RequiredArgsConstructor
public class DispositivoController {

    private final PushService pushService;

    // La app (Capacitor) llama aqui al obtener su token de FCM.
    @PostMapping("/token")
    public ResponseEntity<Void> registrar(@RequestBody @Valid TokenRequestDTO request) {
        pushService.registrar(request.getToken(), request.getPlataforma());
        return ResponseEntity.noContent().build();
    }

    // Al cerrar sesion se puede dar de baja el token (opcional).
    @DeleteMapping("/token")
    public ResponseEntity<Void> eliminar(@RequestBody TokenRequestDTO request) {
        pushService.eliminar(request.getToken());
        return ResponseEntity.noContent().build();
    }
}
