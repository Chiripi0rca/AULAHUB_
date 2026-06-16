import { api } from "./interceptor.js";
import { clearSession, getSession, saveSession } from "./auth-session.js";

// Adaptador de autenticacion contra /api/auth.
export async function login(email, password) {
  const response = await api.post("/auth/login", { email, password });
  return saveSession(response || {}, email);
}

export async function forgotPassword(email) {
  return api.post("/auth/forgot-password", { email });
}

export async function resetPassword(token, password) {
  // El backend espera el campo "nuevaPassword" (ver ResetPasswordRequestDTO).
  return api.post("/auth/reset-password", { token, nuevaPassword: password });
}

// Cambio directo de contraseña estando autenticado (valida la contraseña actual).
export async function cambiarPassword(passwordActual, nuevaPassword) {
  return api.post("/auth/cambiar-password", { passwordActual, nuevaPassword });
}

// Crea una cuenta nueva. Solo SUPER_ADMIN y ADMIN_CENTRAL pueden llamar este endpoint.
export async function registrar(payload) {
  return api.post("/auth/registrar", payload);
}

export async function logout() {
  try {
    const refreshToken = getSession()?.refreshToken;
    if (refreshToken) await api.post("/auth/logout", { refreshToken });
  } catch {
    // Si el servidor falla igual limpiamos localmente
  } finally {
    clearSession();
  }
}
