package com.aulahub.backend.service;

import com.aulahub.backend.model.DeviceTokenEntity;
import com.aulahub.backend.model.UserEntity;
import com.aulahub.backend.repository.DeviceTokenRepository;
import com.aulahub.backend.repository.UserRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Gestiona los tokens de dispositivo y envia notificaciones push via FCM.
@Service
@RequiredArgsConstructor
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    // Registra (o reasigna) el token del dispositivo del usuario autenticado.
    @Transactional
    public void registrar(String token, String plataforma) {
        if (token == null || token.isBlank()) return;
        UserEntity user = usuarioActual();
        if (user == null) return;

        deviceTokenRepository.findByToken(token).ifPresentOrElse(
                dt -> {                       // el token ya existia: reasignar al usuario actual
                    dt.setUsuario(user);
                    dt.setPlataforma(plataforma);
                    deviceTokenRepository.save(dt);
                },
                () -> deviceTokenRepository.save(DeviceTokenEntity.builder()
                        .usuario(user)
                        .token(token)
                        .plataforma(plataforma)
                        .build())
        );
    }

    @Transactional
    public void eliminar(String token) {
        if (token != null && !token.isBlank()) deviceTokenRepository.deleteByToken(token);
    }

    // Envia un push a TODOS los dispositivos del usuario. Asincrono y best-effort:
    // si FCM no esta configurado o el envio falla, no afecta la accion que lo origino.
    @Async
    @Transactional
    public void enviarPush(Long usuarioId, String titulo, String cuerpo) {
        if (FirebaseApp.getApps().isEmpty()) return; // FCM deshabilitado
        List<DeviceTokenEntity> tokens = deviceTokenRepository.findByUsuarioId(usuarioId);

        for (DeviceTokenEntity dt : tokens) {
            try {
                Message mensaje = Message.builder()
                        .setToken(dt.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(titulo)
                                .setBody(cuerpo)
                                .build())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setColor("#164B8A")     // color de marca AulaHub
                                        .setSound("default")
                                        .build())
                                .build())
                        .putData("tipo", "notificacion")
                        .build();

                FirebaseMessaging.getInstance().send(mensaje);
                log.info("Push enviado a usuario {} (token ...{})", usuarioId, sufijo(dt.getToken()));
            } catch (FirebaseMessagingException e) {
                MessagingErrorCode code = e.getMessagingErrorCode();
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                    deviceTokenRepository.deleteByToken(dt.getToken()); // token muerto: limpiarlo
                }
                log.warn("Push fallido (usuario {}): {}", usuarioId, e.getMessage());
            } catch (Exception e) {
                log.error("Error inesperado enviando push a usuario {}: {}", usuarioId, e.getMessage());
            }
        }
    }

    private UserEntity usuarioActual() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    private String sufijo(String token) {
        return token.length() > 6 ? token.substring(token.length() - 6) : token;
    }
}
