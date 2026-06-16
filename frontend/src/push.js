// Notificaciones push (FCM) en la app movil. Solo corre dentro del APK (Capacitor nativo);
// en el navegador web no hace nada. Registra el dispositivo en el backend y muestra un
// aviso (toast) cuando llega una notificacion con la app ABIERTA. Con la app cerrada o en
// segundo plano, Android muestra la notificacion en la bandeja automaticamente.
import { api } from "./api/interceptor.js";
import { mostrarToast } from "./layout.js";

let pushIniciado = false;

export async function iniciarPush() {
    const Cap = window.Capacitor;
    // Solo en la app nativa (no en el navegador web).
    if (!Cap || typeof Cap.isNativePlatform !== "function" || !Cap.isNativePlatform()) return;

    const Push = Cap.Plugins?.PushNotifications;
    if (!Push || pushIniciado) return;
    pushIniciado = true;

    try {
        // 1) Permiso de notificaciones.
        let permiso = await Push.checkPermissions();
        if (permiso.receive === "prompt" || permiso.receive === "prompt-with-rationale") {
            permiso = await Push.requestPermissions();
        }
        if (permiso.receive !== "granted") return;

        // 2) Token de FCM -> se guarda en el backend (para este usuario).
        await Push.addListener("registration", async (token) => {
            try {
                await api.post("/dispositivos/token", { token: token.value, plataforma: "android" });
            } catch (e) {
                console.warn("No se pudo registrar el token de push:", e);
            }
        });

        await Push.addListener("registrationError", (err) => {
            console.warn("Error registrando push:", err);
        });

        // 3) Notificacion recibida con la app ABIERTA -> aviso (toast).
        await Push.addListener("pushNotificationReceived", (noti) => {
            const titulo = noti?.title || "Notificación";
            const cuerpo = noti?.body || "";
            mostrarToast(cuerpo ? `${titulo}: ${cuerpo}` : titulo, "aviso");
        });

        // 4) El usuario toca la notificacion (app en segundo plano/cerrada) -> abre la app.
        await Push.addListener("pushNotificationActionPerformed", () => {
            // Aqui se podria navegar a una pantalla concreta; por ahora solo abre la app.
        });

        // 5) Registra en FCM (dispara el listener 'registration' con el token).
        await Push.register();
    } catch (e) {
        console.warn("No se pudo iniciar push:", e);
    }
}
