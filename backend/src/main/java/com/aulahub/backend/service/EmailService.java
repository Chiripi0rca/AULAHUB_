package com.aulahub.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

// Envia correos HTML por SMTP usando JavaMailSender (configurado con spring.mail.*).
// Es OPCIONAL: si no hay SMTP configurado (spring.mail.host vacio), el envio se
// deshabilita sin romper nada. Todos los metodos son @Async (no bloquean la
// peticion) y best-effort (si fallan, se loguean y no tumban la accion origen).
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    // ObjectProvider: si no hay bean de JavaMailSender (sin SMTP), queda deshabilitado.
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    // Remitente = el usuario autenticado del SMTP.
    @Value("${spring.mail.username:}")
    private String from;

    @Value("${frontend.url:http://localhost}")
    private String frontendUrl;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    // Email HTML con boton para restablecer la contrasena. El enlace expira en 30 min.
    @Async
    public void enviarEmailRecuperacion(String destinatario, String nombre, String token) {
        String enlace = frontendUrl + "/pages/restablecer-password.html?token=" + token;

        String html = """
            <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;color:#1f2937;">
              <div style="background:#164B8A;padding:24px;border-radius:12px 12px 0 0;text-align:center;">
                <h1 style="color:#fff;margin:0;font-size:20px;">AulaHub</h1>
              </div>
              <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:28px;">
                <p style="font-size:15px;">Hola <strong>%s</strong>,</p>
                <p style="font-size:14px;color:#4b5563;">
                  Recibimos una solicitud para restablecer tu contraseña en AulaHub.
                  Haz clic en el botón para crear una nueva:
                </p>
                <div style="text-align:center;margin:28px 0;">
                  <a href="%s" style="background:#164B8A;color:#fff;text-decoration:none;
                     padding:13px 28px;border-radius:8px;font-weight:600;font-size:15px;display:inline-block;">
                    Restablecer contraseña
                  </a>
                </div>
                <p style="font-size:13px;color:#6b7280;">
                  Este enlace expira en <strong>30 minutos</strong>.
                </p>
                <p style="font-size:12px;color:#9ca3af;border-top:1px solid #f3f4f6;padding-top:14px;margin-top:20px;">
                  Si el botón no funciona, copia y pega este enlace en tu navegador:<br>
                  <a href="%s" style="color:#164B8A;word-break:break-all;">%s</a>
                </p>
                <p style="font-size:12px;color:#9ca3af;">
                  Si no solicitaste este cambio, ignora este correo.
                </p>
                <p style="font-size:12px;color:#9ca3af;margin-top:18px;">AulaHub — Sistema de Reservas FIC</p>
              </div>
            </div>
            """.formatted(escapeHtml(nombre), enlace, enlace, enlace);

        enviar(destinatario, "Recuperación de contraseña — AulaHub", html);
    }

    // Notificacion simple (sin tabla). Delega en la version detallada con detalles nulos.
    @Async
    public void enviarNotificacionReserva(String destinatario, String nombre, String asunto, String cuerpo) {
        enviarNotificacionReservaDetallada(destinatario, nombre, asunto, cuerpo, null);
    }

    // Notificacion de reserva con TABLA de detalle (aula, fecha, horario, materia, etc.).
    // 'intro' es el mensaje breve; 'detalles' es un mapa etiqueta->valor que se pinta como tabla.
    @Async
    public void enviarNotificacionReservaDetallada(String destinatario, String nombre, String asunto,
                                                   String intro, Map<String, String> detalles) {
        String a = asunto.toLowerCase();
        String color, etiqueta;
        if (a.contains("acept"))          { color = "#16a34a"; etiqueta = "Reserva aceptada"; }
        else if (a.contains("rechaz"))    { color = "#dc2626"; etiqueta = "Reserva rechazada"; }
        else if (a.contains("cancel"))    { color = "#ea580c"; etiqueta = "Reserva cancelada"; }
        else if (a.contains("solicitud")) { color = "#164B8A"; etiqueta = "Nueva solicitud"; }
        else                              { color = "#164B8A"; etiqueta = asunto; }

        String html = """
            <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;color:#1f2937;">
              <div style="background:%s;padding:22px;border-radius:12px 12px 0 0;text-align:center;">
                <h1 style="color:#fff;margin:0;font-size:19px;">%s</h1>
                <p style="color:rgba(255,255,255,.85);margin:6px 0 0;font-size:13px;">AulaHub</p>
              </div>
              <div style="border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;padding:26px;">
                <p style="font-size:15px;margin:0 0 16px;">Hola <strong>%s</strong>,</p>
                <div style="background:#f9fafb;border-left:4px solid %s;border-radius:8px;padding:14px 16px;font-size:14px;color:#374151;line-height:1.55;">
                  %s
                </div>
                %s
                <p style="font-size:12px;color:#9ca3af;border-top:1px solid #f3f4f6;padding-top:14px;margin-top:18px;">
                  Mensaje automatico — AulaHub, Sistema de Reservas FIC.
                </p>
              </div>
            </div>
            """.formatted(color, escapeHtml(etiqueta), escapeHtml(nombre), color,
                          escapeHtml(intro), tablaDetalles(detalles));

        enviar(destinatario, asunto + " — AulaHub", html);
    }

    // ── Envio HTML por SMTP ───────────────────────────────────────────────────
    // Best-effort: captura sus propias excepciones y solo las loguea; el correo
    // nunca debe tumbar la accion que lo origino (reserva, recuperacion, etc.).
    private void enviar(String destinatario, String asunto, String html) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("SMTP no configurado (spring.mail.host vacio): no se envia el correo a {}", destinatario);
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);   // true = el cuerpo es HTML
            mailSender.send(mime);
            log.info("Correo enviado por SMTP a {} — {}", destinatario, asunto);
        } catch (Exception e) {
            log.error("Fallo al enviar correo por SMTP a {}: {}", destinatario, e.getMessage());
        }
    }

    // Construye la tabla HTML de "Detalle de la reserva" a partir del mapa etiqueta->valor.
    private String tablaDetalles(Map<String, String> detalles) {
        if (detalles == null || detalles.isEmpty()) return "";

        StringBuilder filas = new StringBuilder();
        for (Map.Entry<String, String> e : detalles.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) continue;
            filas.append("""
                <tr>
                  <td style="padding:9px 14px;color:#6b7280;font-size:13px;border-bottom:1px solid #eef0f2;white-space:nowrap;vertical-align:top;">%s</td>
                  <td style="padding:9px 14px;color:#111827;font-size:13px;font-weight:600;border-bottom:1px solid #eef0f2;">%s</td>
                </tr>
                """.formatted(escapeHtml(e.getKey()), escapeHtml(e.getValue())));
        }

        // OJO: en un text block con .formatted() el '%' literal debe escaparse como '%%'.
        return """
            <p style="font-size:13px;font-weight:700;color:#374151;margin:20px 0 8px;">Detalle de la reserva</p>
            <table style="width:100%%;border-collapse:collapse;border:1px solid #eef0f2;border-radius:8px;overflow:hidden;">
              %s
            </table>
            """.formatted(filas.toString());
    }

    // Escapa caracteres HTML para insertar texto del sistema de forma segura en el correo.
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
