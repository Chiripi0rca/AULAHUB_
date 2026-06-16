// Página de selección de aula: muestra solo las facultades del usuario y sus aulas.
// ADMIN_CENTRAL ve todas. Los demás roles ven solo las facultades a las que pertenecen.
import { aulasApi, facultadesApi } from "../api/catalogos.js";
import { rolesApi } from "../api/gestion.js";
import { getCurrentUser, hasRole, isAdminCentral } from "../api/auth-session.js";
import { escapeHtml, mostrarToast, pageUrl } from "../layout.js";

let facultadSeleccionada = null;

document.addEventListener("DOMContentLoaded", async () => {
    mostrarBannerRol();
    await cargarFacultades();
});

// Panel de inicio por rol: saludo personalizado + tarjetas grandes de accesos directos.
function mostrarBannerRol() {
    const banner = document.getElementById("banner-rol");
    if (!banner) return;

    const user = getCurrentUser();
    const nombre = (user?.nombre || "").split(" ")[0] || "";

    let rolTexto, accesos;
    if (isAdminCentral()) {
        rolTexto = "Administrador Central";
        accesos = [
            ["dashboard.html",        "dashboard", "Dashboard",      "Estadísticas globales del sistema"],
            ["solicitudes.html",      "inbox",     "Solicitudes",    "Revisa reservas pendientes"],
            ["personas.html",         "group",     "Personas",       "Usuarios, roles y registro"],
            ["gestion-catalogos.html","folder",    "Catálogos",      "Aulas, materias, grupos y tipos"],
            ["facultades.html",       "apartment", "Facultades",     "Crear y administrar facultades"],
        ];
    } else if (hasRole("SUPER_ADMIN")) {
        rolTexto = "Super Admin";
        accesos = [
            ["dashboard.html",        "dashboard", "Dashboard",      "Estadísticas de tu facultad"],
            ["solicitudes.html",      "inbox",     "Solicitudes",    "Aprueba o rechaza reservas"],
            ["personas.html",         "group",     "Personas",       "Usuarios y roles de tu facultad"],
            ["gestion-catalogos.html","folder",    "Catálogos",      "Aulas, materias, grupos y tipos"],
            ["asignar-materias.html", "assignment_ind", "Asignar materias", "Materias por maestro"],
        ];
    } else if (hasRole("ADMIN")) {
        rolTexto = "Administrador";
        accesos = [
            ["solicitudes.html", "inbox",     "Solicitudes", "Revisa las reservas de tus aulas"],
            ["dashboard.html",   "dashboard", "Dashboard",   "Resumen de tus aulas"],
            ["mis-reservas.html","event_note","Historial",   "Todas las reservas"],
        ];
    } else {
        // PROFESOR: saludo simple, su flujo es reservar (abajo estan las facultades/aulas)
        banner.innerHTML = `
          <div class="inicio-hero">
            <h3>Hola${nombre ? ", " + escapeHtml(nombre) : ""} 👋</h3>
            <p>Selecciona una facultad y un aula para crear tu reserva.</p>
          </div>`;
        return;
    }

    banner.innerHTML = `
      <div class="inicio-hero">
        <h3>Hola${nombre ? ", " + nombre : ""} 👋</h3>
        <p>${rolTexto} — accede rápido a tus tareas:</p>
      </div>
      <div class="inicio-cards">
        ${accesos.map(([page, icono, titulo, desc]) => `
          <a href="${pageUrl(page)}" class="inicio-card">
            <span class="material-symbols-outlined inicio-card-icon">${icono}</span>
            <strong>${titulo}</strong>
            <span class="inicio-card-desc">${desc}</span>
          </a>`).join("")}
      </div>`;
}

// ── Facultades como tarjetas ──────────────────────────────────────────────────

async function cargarFacultades() {
    const contenedor = document.getElementById("facultad-list");
    if (!contenedor) return;

    contenedor.innerHTML = `<p style="color:#888">Cargando facultades...</p>`;

    try {
        // Cargar todas las facultades activas
        const todas = await facultadesApi.listar();
        const activas = (todas || []).filter(f => f.activa);

        let facultadesAMostrar;

        if (isAdminCentral()) {
            // ADMIN_CENTRAL ve todas las facultades
            facultadesAMostrar = activas;
        } else {
            // Los demás roles solo ven sus propias facultades
            const misRoles = await rolesApi.misRoles();
            const misFacultadIds = new Set(
                (misRoles || [])
                    .filter(r => r.facultadId !== null && r.facultadId !== undefined)
                    .map(r => r.facultadId)
            );
            facultadesAMostrar = activas.filter(f => misFacultadIds.has(f.id));
        }

        if (facultadesAMostrar.length === 0) {
            contenedor.innerHTML = `<p style="color:#888">No tienes facultades asignadas.</p>`;
            return;
        }

        contenedor.innerHTML = facultadesAMostrar.map(f => `
            <div class="aula-card-classic card facultad-card"
                 data-id="${f.id}">
                <div class="aula-emoji">🏛️</div>
                <div class="aula-text">
                    <strong>${escapeHtml(f.nombre)}</strong>
                    <span>${escapeHtml(f.clave)}</span>
                </div>
            </div>
        `).join("");

        contenedor.querySelectorAll(".facultad-card").forEach(card => {
            card.addEventListener("click", () => seleccionarFacultad(card));
        });

        // Seleccionar la primera automáticamente
        contenedor.querySelector(".facultad-card")?.click();

    } catch {
        contenedor.innerHTML = `<p style="color:#888">Error al cargar las facultades.</p>`;
        mostrarToast("No se pudieron cargar las facultades.", "error");
    }
}

function seleccionarFacultad(cardSeleccionada) {
    document.querySelectorAll(".facultad-card").forEach(c => c.classList.remove("card-seleccionada"));
    cardSeleccionada.classList.add("card-seleccionada");
    facultadSeleccionada = Number(cardSeleccionada.dataset.id);
    cargarAulas(facultadSeleccionada);
}

// ── Aulas ─────────────────────────────────────────────────────────────────────

async function cargarAulas(facultadId) {
    const contenedor = document.getElementById("aula-list");
    const subtitulo  = document.getElementById("subtitulo-aulas");
    if (!contenedor) return;

    contenedor.innerHTML = `<p style="color:#888">Cargando aulas...</p>`;
    if (subtitulo) subtitulo.style.display = "";

    try {
        const aulas = await aulasApi.listar(facultadId);

        if (!aulas || aulas.length === 0) {
            contenedor.innerHTML = `<p style="color:#888">No hay aulas disponibles para esta facultad.</p>`;
            return;
        }

        contenedor.innerHTML = aulas.map(aula => `
            <div class="aula-card-classic card"
                 style="cursor:pointer"
                 onclick="location.href='${pageUrl("reserva.html")}?aulaId=${aula.id}'">
                <div class="aula-emoji">${iconoAula(aula.codigo)}</div>
                <div class="aula-text">
                    <strong>${escapeHtml(aula.nombre)}</strong>
                    <span>${escapeHtml(aula.codigo)}</span>
                </div>
            </div>
        `).join("");

    } catch {
        contenedor.innerHTML = "";
        mostrarToast("No se pudieron cargar las aulas.", "error");
    }
}

// ── Helper ────────────────────────────────────────────────────────────────────

function iconoAula(codigo = "") {
    const c = codigo.toLowerCase();
    if (c.includes("auditorio")) return "🎤";
    if (c.includes("lab"))       return "💻";
    return "🏫";
}
