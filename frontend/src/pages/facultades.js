// Gestión de facultades — exclusivo para ADMIN_CENTRAL: crear, editar, activar y desactivar.
import { facultadesApi } from "../api/catalogos.js";
import { isAdminCentral } from "../api/auth-session.js";
import { escapeHtml, mostrarToast, pageUrl } from "../layout.js";
import { protegerRuta } from "../route-guard.js";

protegerRuta();
if (!isAdminCentral()) window.location.replace(pageUrl("aulas.html"));

document.addEventListener("DOMContentLoaded", async () => {
    await cargarFacultades();

    document.getElementById("btn-guardar")
        .addEventListener("click", manejarGuardar);

    document.getElementById("btn-cancelar")
        .addEventListener("click", cancelarEdicion);

    // Forzar mayúsculas en tiempo real
    document.getElementById("fac-clave")
        .addEventListener("input", e => { e.target.value = e.target.value.toUpperCase(); });
});

// ── Lista ─────────────────────────────────────────────────────────────────────

async function cargarFacultades() {
    const contenedor = document.getElementById("lista-facultades");
    if (!contenedor) return;

    try {
        const facultades = await facultadesApi.listar();

        if (!facultades || facultades.length === 0) {
            contenedor.innerHTML = `<p class="status-message">No hay facultades registradas.</p>`;
            return;
        }

        contenedor.innerHTML = facultades.map(f => `
            <div class="gestion-fila">
                <div class="gestion-fila-info">
                    <span class="material-symbols-outlined" style="color:#164B8A; font-size:22px;">school</span>
                    <div>
                        <strong style="display:block; color:#1f2937;">${escapeHtml(f.nombre)}</strong>
                        <span style="font-size:13px; color:#6b7280;">${escapeHtml(f.clave)}</span>
                    </div>
                    <span class="badge ${f.activa ? 'badge-activo' : 'badge-inactivo'}">
                        ${f.activa ? 'Activa' : 'Inactiva'}
                    </span>
                </div>
                <div class="gestion-fila-acciones">
                    <button class="btn-icono btn-icono-editar" title="Editar"
                            data-id="${f.id}" data-nombre="${escapeHtml(f.nombre)}" data-clave="${escapeHtml(f.clave)}">
                        <span class="material-symbols-outlined" style="font-size:18px;">edit</span>
                    </button>
                    ${f.activa
                        ? `<button class="btn-icono btn-icono-danger btn-desactivar" title="Desactivar" data-id="${f.id}">
                               <span class="material-symbols-outlined" style="font-size:18px;">block</span>
                           </button>`
                        : `<button class="btn-icono btn-icono-exito btn-activar" title="Activar" data-id="${f.id}">
                               <span class="material-symbols-outlined" style="font-size:18px;">check_circle</span>
                           </button>`
                    }
                </div>
            </div>
        `).join("");

        // Eventos
        contenedor.querySelectorAll(".btn-icono-editar").forEach(btn =>
            btn.addEventListener("click", () => iniciarEdicion(btn.dataset))
        );
        contenedor.querySelectorAll(".btn-desactivar").forEach(btn =>
            btn.addEventListener("click", () => desactivar(Number(btn.dataset.id)))
        );
        contenedor.querySelectorAll(".btn-activar").forEach(btn =>
            btn.addEventListener("click", () => activar(Number(btn.dataset.id)))
        );

    } catch {
        contenedor.innerHTML = `<p class="status-message">Error al cargar las facultades.</p>`;
    }
}

// ── Guardar (crear o editar) ───────────────────────────────────────────────────

async function manejarGuardar() {
    limpiarErrores();

    const id     = document.getElementById("fac-id").value;
    const nombre = document.getElementById("fac-nombre").value.trim();
    const clave  = document.getElementById("fac-clave").value.trim().toUpperCase();

    let ok = true;
    if (!nombre) { setError("fac-nombre", "El nombre es obligatorio."); ok = false; }
    if (!clave)  { setError("fac-clave",  "La clave es obligatoria.");  ok = false; }
    else if (!/^[A-Z]{2,10}$/.test(clave)) {
        setError("fac-clave", "Solo letras mayúsculas, entre 2 y 10 caracteres.");
        ok = false;
    }
    if (!ok) return;

    const btn = document.getElementById("btn-guardar");
    btn.disabled = true;
    btn.innerHTML = `<span class="material-symbols-outlined">hourglass_top</span> Guardando...`;

    try {
        if (id) {
            await facultadesApi.actualizar(Number(id), { nombre, clave });
        } else {
            await facultadesApi.crear({ nombre, clave });
        }
        mostrar("fac-exito");
        cancelarEdicion();
        await cargarFacultades();
    } catch (err) {
        const el = document.getElementById("fac-error");
        el.textContent = err.message || "Error al guardar la facultad.";
        mostrar("fac-error");
    } finally {
        btn.disabled = false;
        btn.innerHTML = `<span class="material-symbols-outlined">save</span> Guardar`;
    }
}

function iniciarEdicion({ id, nombre, clave }) {
    document.getElementById("fac-id").value     = id;
    document.getElementById("fac-nombre").value = nombre;
    document.getElementById("fac-clave").value  = clave;
    document.getElementById("form-titulo").textContent = "Editar facultad";
    mostrar("btn-cancelar");
    limpiarErrores();
    document.getElementById("fac-nombre").focus();
    document.getElementById("form-facultad")
        .scrollIntoView({ behavior: "smooth", block: "start" });
}

function cancelarEdicion() {
    document.getElementById("fac-id").value     = "";
    document.getElementById("fac-nombre").value = "";
    document.getElementById("fac-clave").value  = "";
    document.getElementById("form-titulo").textContent = "Nueva facultad";
    ocultar("btn-cancelar");
}

// ── Desactivar / Activar ──────────────────────────────────────────────────────

async function desactivar(id) {
    if (!confirm("¿Desactivar esta facultad?\nTambién se desactivarán todas sus aulas, materias y tipos de reserva.")) return;
    try {
        await facultadesApi.desactivar(id);
        mostrarToast("Facultad desactivada.", "exito");
        await cargarFacultades();
    } catch (err) {
        mostrarToast(err.message || "Error al desactivar.", "error");
    }
}

async function activar(id) {
    try {
        await facultadesApi.activar(id);
        mostrarToast("Facultad activada.", "exito");
        await cargarFacultades();
    } catch (err) {
        mostrarToast(err.message || "Error al activar.", "error");
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

const ocultar = id => { const el = document.getElementById(id); if (el) el.style.display = "none"; };
const mostrar = id => { const el = document.getElementById(id); if (el) el.style.display = "block"; };

function setError(inputId, msg) {
    document.getElementById(inputId)?.classList.add("input-error");
    const el = document.getElementById(`err-${inputId}`);
    if (el) el.textContent = msg;
}

function limpiarErrores() {
    ["fac-nombre", "fac-clave"].forEach(id => {
        document.getElementById(id)?.classList.remove("input-error");
        const err = document.getElementById(`err-${id}`);
        if (err) err.textContent = "";
    });
    ocultar("fac-exito");
    ocultar("fac-error");
}
