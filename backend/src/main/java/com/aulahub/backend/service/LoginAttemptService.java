package com.aulahub.backend.service;

import com.aulahub.backend.exception.DemasiadosIntentosException;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Control de intentos de login por IP para frenar ataques de fuerza bruta.
// Almacenamiento en memoria (suficiente para una sola instancia). Si el sistema
// escalara a varias instancias, esto debería moverse a Redis o similar.
@Service
public class LoginAttemptService {

    private static final int  MAX_INTENTOS = 5;            // intentos fallidos permitidos
    private static final long VENTANA_MS   = 15 * 60_000L; // dentro de 15 minutos

    // Limite para forgot-password: cada solicitud dispara un correo, asi que se
    // cuentan TODAS las solicitudes (no solo fallos) para evitar spam de correos
    // y que alguien sature el envio por SMTP.
    private static final int  MAX_RECUPERACIONES      = 3;
    private static final long VENTANA_RECUPERACION_MS = 15 * 60_000L;

    // IP → timestamps de los intentos fallidos recientes
    private final Map<String, Deque<Long>> intentosPorIp = new ConcurrentHashMap<>();

    // IP -> timestamps de solicitudes de recuperacion de password recientes
    private final Map<String, Deque<Long>> recuperacionesPorIp = new ConcurrentHashMap<>();

    // Llamar ANTES de intentar autenticar: si la IP está bloqueada, corta el flujo.
    public void verificarBloqueo(String ip) {
        Deque<Long> intentos = intentosPorIp.get(ip);
        if (intentos == null) return;

        synchronized (intentos) {
            limpiarViejos(intentos, VENTANA_MS);
            if (intentos.size() >= MAX_INTENTOS) {
                throw new DemasiadosIntentosException(
                        "Demasiados intentos fallidos. Espera unos minutos antes de volver a intentar.");
            }
        }
    }

    // Llamar cuando el login FALLA por credenciales incorrectas.
    public void registrarFallo(String ip) {
        Deque<Long> intentos = intentosPorIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (intentos) {
            limpiarViejos(intentos, VENTANA_MS);
            intentos.addLast(System.currentTimeMillis());
        }
    }

    // Llamar cuando el login es EXITOSO: limpia el historial de esa IP.
    public void limpiarIntentos(String ip) {
        intentosPorIp.remove(ip);
    }

    // Llamar en CADA solicitud de forgot-password: registra la solicitud y corta
    // si la IP supera el limite dentro de la ventana.
    public void verificarYRegistrarRecuperacion(String ip) {
        Deque<Long> solicitudes = recuperacionesPorIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (solicitudes) {
            limpiarViejos(solicitudes, VENTANA_RECUPERACION_MS);
            if (solicitudes.size() >= MAX_RECUPERACIONES) {
                throw new DemasiadosIntentosException(
                        "Demasiadas solicitudes de recuperación. Espera unos minutos antes de volver a intentar.");
            }
            solicitudes.addLast(System.currentTimeMillis());
        }
    }

    // Descarta los timestamps fuera de la ventana de tiempo
    private void limpiarViejos(Deque<Long> registros, long ventanaMs) {
        long limite = System.currentTimeMillis() - ventanaMs;
        while (!registros.isEmpty() && registros.peekFirst() < limite) {
            registros.pollFirst();
        }
    }
}
