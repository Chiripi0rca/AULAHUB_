const SESSION_KEY   = "aulahub_session";

// Guarda y lee la sesion JWT que entrega el backend Spring.
function normalizeRole(role) {
  if (!role) return "PROFESOR";
  if (typeof role === "string") return role;
  return role.tipo || role.rol || role.authority || "PROFESOR";
}

function normalizeUser(rawUser = {}, fallbackEmail = "") {
  return {
    id: rawUser.id ?? rawUser.usuarioId ?? rawUser.userId ?? rawUser.uid ?? null,
    email: rawUser.email ?? fallbackEmail,
    nombre: rawUser.nombre ?? rawUser.name ?? rawUser.email ?? fallbackEmail,
    fotoUrl: rawUser.fotoUrl ?? rawUser.FotoPerfil ?? rawUser.photoURL ?? "",
    roles: Array.isArray(rawUser.roles) ? rawUser.roles.map(normalizeRole) : [],
    raw: rawUser,
  };
}

function parseJwtPayload(token) {
  if (!token || !token.includes(".")) return {};
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(decodeURIComponent(escape(atob(payload))));
  } catch {
    return {};
  }
}

export function saveSession(authResponse = {}, fallbackEmail = "") {
  const accessToken = authResponse.accessToken || authResponse.token || authResponse.jwt || "";
  const refreshToken = authResponse.refreshToken || authResponse.refresh || "";
  const payload = parseJwtPayload(accessToken);
  const rawUser = authResponse.usuario || authResponse.user || authResponse.usuarioResponse || {
    id: authResponse.usuarioId || authResponse.userId || payload.userId || payload.id || payload.sub,
    email: authResponse.email || payload.email || payload.sub || fallbackEmail,
    nombre: authResponse.nombre || payload.nombre || payload.name,
    roles: authResponse.roles || payload.roles || payload.authorities,
    fotoUrl: authResponse.fotoUrl || payload.fotoUrl || null,
  };

  const session = {
    accessToken,
    refreshToken,
    user: normalizeUser(rawUser, fallbackEmail),
  };

  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  return session;
}

export function getSession() {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY)) || null;
  } catch {
    return null;
  }
}

export function getAccessToken() {
  return getSession()?.accessToken || "";
}

export function getCurrentUser() {
  return getSession()?.user || null;
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY);
  localStorage.removeItem("user_photo");
}

export function isAuthenticated() {
  return Boolean(getAccessToken());
}

export function hasRole(...roles) {
  const userRoles = getCurrentUser()?.roles || [];
  return roles.some((role) => userRoles.includes(role));
}

export function isAdminUser() {
  return hasRole("ADMIN", "SUPER_ADMIN", "ADMIN_CENTRAL");
}

// Solo el ADMIN_CENTRAL puede gestionar todas las facultades.
export function isAdminCentral() {
  return hasRole("ADMIN_CENTRAL");
}
