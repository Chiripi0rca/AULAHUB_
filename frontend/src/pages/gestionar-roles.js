// Gestión de roles por facultad.
// ADMIN_CENTRAL ve todas las facultades y todos los roles.
// SUPER_ADMIN ve solo su facultad y solo ADMIN / PROFESOR.
import { facultadesApi } from "../api/catalogos.js";
import { rolesApi } from "../api/gestion.js";
import { isAdminCentral } from "../api/auth-session.js";
import { escapeHtml, mostrarToast } from "../layout.js";
import { soloAdmin } from "../route-guard.js";

soloAdmin();

// ── Constantes de presentación ────────────────────────────────────────────────

const LABEL_ROL = {
    PROFESOR:     "Profesor",
    ADMIN:        "Admin",
    SUPER_ADMIN:  "Super Admin",
    ADMIN_CENTRAL:"Admin Central",
};

const ICONO_ROL = {
    PROFESOR:     "📚",
    ADMIN:        "🛡️",
    SUPER_ADMIN:  "⭐",
    ADMIN_CENTRAL:"🌐",
};

const LABEL_TURNO = {
    MATUTINO:  "Matutino",
    VESPERTINO:"Vespertino",
    AMBOS:     "Ambos turnos",
};

// ── Inicialización ────────────────────────────────────────────────────────────

document.addEventListener("DOMContentLoaded", () => cargarFacultades());

// ── Facultades (acordeón) ─────────────────────────────────────────────────────

async function cargarFacultades() {
    const contenedor = document.getElementById("facultades-contenedor");

    try {
        const todasFacultades = await facultadesApi.listar();
        const activas = (todasFacultades || []).filter(f => f.activa);

        const visibles = isAdminCentral()
            ? activas
            : await filtrarFacultadesPropias(activas);

        if (visibles.length === 0) {
            contenedor.innerHTML = `<p class="status-message">No tienes facultades asignadas.</p>`;
            return;
        }

        contenedor.innerHTML = visibles.map(f => `
            <div class="facultad-acordeon" data-facultad-id="${f.id}">
              <div class="facultad-acordeon-header">
                <div class="facultad-acordeon-titulo">
                  <span>🏛️</span>
                  <span>${escapeHtml(f.nombre)}</span>
                  <span class="facultad-acordeon-clave">${escapeHtml(f.clave)}</span>
                </div>
                <span class="material-symbols-outlined facultad-acordeon-chevron">expand_more</span>
              </div>
              <div class="facultad-acordeon-body">
                <p class="status-message" style="font-size:13px; padding:8px 0;">
                  Haz clic para ver los usuarios.
                </p>
              </div>
            </div>
        `).join("");

        contenedor.querySelectorAll(".facultad-acordeon-header").forEach(header =>
            header.addEventListener("click", () => onClickFacultad(header))
        );

    } catch (err) {
        contenedor.innerHTML = `<p class="status-message">Error al cargar las facultades.</p>`;
        mostrarToast("No se pudieron cargar las facultades.", "error");
    }
}

async function filtrarFacultadesPropias(activas) {
    const misRoles   = await rolesApi.misRoles();
    const misFacIds  = new Set(misRoles.map(r => r.facultadId).filter(Boolean));
    return activas.filter(f => misFacIds.has(f.id));
}

function onClickFacultad(header) {
    const acordeon   = header.closest(".facultad-acordeon");
    const body       = acordeon.querySelector(".facultad-acordeon-body");
    const chevron    = header.querySelector(".facultad-acordeon-chevron");
    const facultadId = Number(acordeon.dataset.facultadId);
    const estaAbierto = body.classList.contains("abierto");

    // Cerrar
    if (estaAbierto) {
        body.classList.remove("abierto");
        chevron.classList.remove("abierto");
        return;
    }

    // Abrir
    body.classList.add("abierto");
    chevron.classList.add("abierto");

    // Cargar solo si no se ha cargado antes
    if (body.dataset.cargado !== "true") {
        cargarUsuarios(facultadId, body, acordeon);
    }
}

// ── Carga de usuarios de una facultad ────────────────────────────────────────

async function cargarUsuarios(facultadId, body, acordeon) {
    body.innerHTML = `<p class="status-message" style="font-size:13px; padding:8px 0;">Cargando...</p>`;

    try {
        const roles = await rolesApi.listarPorFacultad(facultadId);

        if (!roles || roles.length === 0) {
            body.innerHTML = `<p class="status-message" style="font-size:13px; padding:8px 0;">
                Sin usuarios en esta facultad.
            </p>`;
            body.dataset.cargado = "true";
            return;
        }

        // Agrupar roles por usuario
        const usuariosMap = new Map();
        for (const rol of roles) {
            if (!usuariosMap.has(rol.usuarioId)) {
                usuariosMap.set(rol.usuarioId, {
                    nombre: rol.usuarioNombre,
                    email:  rol.usuarioEmail,
                    roles:  [],
                });
            }
            usuariosMap.get(rol.usuarioId).roles.push(rol);
        }

        body.innerHTML = Array.from(usuariosMap.values())
            .map(u => renderTarjetaUsuario(u))
            .join("");

        // Asignar eventos a los botones generados
        body.querySelectorAll(".btn-revocar-rol").forEach(btn =>
            btn.addEventListener("click", async () => {
                if (!confirm("¿Revocar este rol?")) return;
                try {
                    await rolesApi.eliminar(Number(btn.dataset.rolId));
                    mostrarToast("Rol revocado.", "exito");
                    recargar(facultadId, body, acordeon);
                } catch (err) {
                    mostrarToast("Error: " + err.message, "error");
                }
            })
        );

        body.querySelectorAll(".btn-quitar-aula").forEach(btn =>
            btn.addEventListener("click", async () => {
                const nombre = btn.dataset.aulaNombre;
                if (!confirm(`¿Quitar el aula "${nombre}" de este rol?`)) return;
                try {
                    await rolesApi.eliminarAula(Number(btn.dataset.rolId), Number(btn.dataset.aulaId));
                    mostrarToast("Aula quitada.", "exito");
                    recargar(facultadId, body, acordeon);
                } catch (err) {
                    mostrarToast("Error: " + err.message, "error");
                }
            })
        );

        body.dataset.cargado = "true";

    } catch (err) {
        body.innerHTML = `<p class="status-message" style="font-size:13px; color:#ef4444; padding:8px 0;">
            Error al cargar los usuarios.
        </p>`;
    }
}

// Borra el cache y recarga el contenido del acordeón abierto
function recargar(facultadId, body, acordeon) {
    delete body.dataset.cargado;
    cargarUsuarios(facultadId, body, acordeon);
}

// ── Render ────────────────────────────────────────────────────────────────────

function renderTarjetaUsuario(usuario) {
    const rolesHtml = usuario.roles.map(renderTarjetaRol).join("");

    return `
        <div class="usuario-card">
            <div class="usuario-card-header">
                <div class="usuario-card-nombre">${escapeHtml(usuario.nombre)}</div>
                <div class="usuario-card-email">${escapeHtml(usuario.email)}</div>
            </div>
            ${rolesHtml}
        </div>`;
}

function renderTarjetaRol(rol) {
    const icono    = ICONO_ROL[rol.tipo]    || "👤";
    const nombre   = LABEL_ROL[rol.tipo]   || rol.tipo;
    const turno    = LABEL_TURNO[rol.turno] || "";

    const aulasHtml = (rol.aulas && rol.aulas.length > 0)
        ? `<div class="rol-card-aulas">
               ${rol.aulas.map(a => `
                   <span class="aula-tag" style="display:inline-flex; align-items:center; gap:4px;">
                       💻 ${escapeHtml(a.nombre)}
                       <button type="button"
                               class="btn-quitar-aula"
                               data-rol-id="${rol.id}"
                               data-aula-id="${a.id}"
                               data-aula-nombre="${escapeHtml(a.nombre)}"
                               title="Quitar aula"
                               style="border:none; background:none; cursor:pointer;
                                      color:#dc2626; font-weight:bold; font-size:14px; padding:0 2px;">×</button>
                   </span>`).join("")}
           </div>`
        : "";

    return `
        <div class="rol-card" style="margin-bottom:6px;">
            <div class="rol-card-header">
                <span class="rol-card-icono">${icono}</span>
                <div class="rol-card-info">
                    <strong>${nombre}</strong>
                    ${turno ? `<span>${turno}</span>` : ""}
                </div>
                <button type="button"
                        class="btn-icono btn-icono-danger btn-revocar-rol"
                        data-rol-id="${rol.id}"
                        title="Revocar rol completo">
                    <span class="material-symbols-outlined" style="font-size:16px;">close</span>
                </button>
            </div>
            ${aulasHtml}
        </div>`;
}
