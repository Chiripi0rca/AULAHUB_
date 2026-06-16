package com.aulahub.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;

// Inicializa el Firebase Admin SDK al arrancar, leyendo la cuenta de servicio desde un archivo.
// Si la ruta no esta configurada o el archivo no existe, el push queda DESHABILITADO
// (solo se loguea un warning) y la app sigue funcionando con normalidad.
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @PostConstruct
    public void init() {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("FCM deshabilitado: 'firebase.credentials-path' no esta configurado.");
            return;
        }
        File archivo = new File(credentialsPath);
        if (!archivo.exists()) {
            log.warn("FCM deshabilitado: no existe el archivo de credenciales en {}", credentialsPath);
            return;
        }
        try (FileInputStream in = new FileInputStream(archivo)) {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(in))
                        .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase inicializado: notificaciones push (FCM) habilitadas.");
            }
        } catch (Exception e) {
            log.error("No se pudo inicializar Firebase (FCM deshabilitado): {}", e.getMessage());
        }
    }
}
