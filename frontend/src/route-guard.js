import { isAdminUser, isAuthenticated } from "./api/auth-session.js";
import { pageUrl } from "./layout.js";

// Protege rutas privadas: redirige segun el rol y estado de sesion del usuario.

// Redirige al login si no hay sesion activa. Usar en todas las paginas protegidas.
export function protegerRuta() {
    if (!isAuthenticated()) {
        window.location.replace(pageUrl("index.html"));
        return false;
    }
    return true;
}

// Redirige a aulas si el usuario no es admin. Usar en paginas exclusivas de admin.
export function soloAdmin() {
    if (!protegerRuta()) return false;
    if (!isAdminUser()) {
        window.location.replace(pageUrl("aulas.html"));
        return false;
    }
    return true;
}

// Redirige a aulas si el usuario ya tiene sesion activa. Usar en la pagina de login.
export function redirigirSiAutenticado() {
    if (isAuthenticated()) {
        window.location.replace(pageUrl("aulas.html"));
        return true;
    }
    return false;
}
