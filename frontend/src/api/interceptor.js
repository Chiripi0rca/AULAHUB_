import { API_BASE_URL } from "./api-config.js";
import { clearSession, getAccessToken, getSession, saveSession } from "./auth-session.js";

// Interceptor HTTP: agrega JWT automaticamente, refresca token expirado y normaliza errores.
function buildUrl(path, query = {}) {
  const cleanPath = path.startsWith("/") ? path : `/${path}`;
  const url = new URL(`${API_BASE_URL}${cleanPath}`);
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, value);
  });
  return url.toString();
}

async function parseResponse(response) {
  if (response.status === 204) return null;
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function refreshAccessToken() {
  const refreshToken = getSession()?.refreshToken;
  if (!refreshToken) return false;

  const response = await fetch(buildUrl("/auth/refresh"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken, token: refreshToken }),
  });

  if (!response.ok) return false;
  const data = await parseResponse(response);
  saveSession(data);
  return true;
}

// Maneja la expiración real de la sesión: un 401 que el refresh ya no pudo recuperar.
// Limpia la sesión, avisa al usuario y lo manda al login. Se ejecuta una sola vez aunque
// varias peticiones fallen a la vez, y solo si HABÍA sesión (así un login con credenciales
// incorrectas —que también responde 401— no dispara el aviso de "sesión expirada").
let _sesionExpiradaManejada = false;

function manejarSesionExpirada() {
  const teniaSesion = Boolean(getSession());
  clearSession();
  if (!teniaSesion || _sesionExpiradaManejada) return;
  _sesionExpiradaManejada = true;

  // Import dinámico para no crear un ciclo con layout.js (que depende del interceptor).
  import("../layout.js").then(({ mostrarToast, pageUrl }) => {
    try { mostrarToast("Tu sesión ha expirado. Inicia sesión de nuevo.", "aviso"); } catch {}
    // Pequeña pausa para que el toast sea visible antes de salir de la página.
    setTimeout(() => { window.location.replace(pageUrl("index.html")); }, 1800);
  });
}

// Rate limiter: bloquea al usuario si supera LIMITE_PETICIONES en VENTANA_MS milisegundos.
const _historialPeticiones = [];
const LIMITE_PETICIONES = 20;
const VENTANA_MS = 10_000;

function verificarRateLimit() {
  const ahora = Date.now();
  while (_historialPeticiones.length && ahora - _historialPeticiones[0] > VENTANA_MS) {
    _historialPeticiones.shift();
  }
  if (_historialPeticiones.length >= LIMITE_PETICIONES) {
    throw new Error("Demasiadas peticiones al servidor. Espera un momento antes de continuar.");
  }
  _historialPeticiones.push(ahora);
}

// Punto de entrada para todas las llamadas REST del frontend.
export async function apiRequest(path, options = {}) {
  verificarRateLimit();
  const { query, retryOnUnauthorized = true, ...fetchOptions } = options;
  const token = getAccessToken();
  const headers = new Headers(fetchOptions.headers || {});

  if (!headers.has("Content-Type") && fetchOptions.body && !(fetchOptions.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(buildUrl(path, query), {
    ...fetchOptions,
    headers,
  });

  if (response.status === 401 && retryOnUnauthorized && await refreshAccessToken()) {
    return apiRequest(path, { ...options, retryOnUnauthorized: false });
  }

  const data = await parseResponse(response);

  if (!response.ok) {
    if (response.status === 401) manejarSesionExpirada();
    const message = data?.message || data?.error || `Error ${response.status}`;
    const err = new Error(typeof message === "string" ? message : JSON.stringify(message));
    err.status = response.status;  // permite diferenciar 400/409/422 en los formularios
    err.data   = data;             // payload completo para errores de campo (400)
    throw err;
  }

  return data;
}

export const api = {
  get: (path, query) => apiRequest(path, { method: "GET", query }),
  post: (path, body) => apiRequest(path, { method: "POST", body: JSON.stringify(body || {}) }),
  put: (path, body) => apiRequest(path, { method: "PUT", body: body === undefined ? undefined : JSON.stringify(body) }),
  delete: (path) => apiRequest(path, { method: "DELETE" }),
};
