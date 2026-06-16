// personas.js — Hub de gestión de personas: accesos rápidos + lista de usuarios embebida.
// Reutiliza la misma lógica que gestion-usuarios.js.
import { usuariosApi } from "../api/gestion.js";
import { hasRole, isAdminCentral } from "../api/auth-session.js";
import { escapeHtml, mostrarToast, pageUrl } from "../layout.js";
import { protegerRuta } from "../route-guard.js";

protegerRuta();
if (!hasRole("SUPER_ADMIN") && !isAdminCentral()) {
  window.location.replace(pageUrl("aulas.html"));
}

let _todos = [];

document.addEventListener("DOMContentLoaded", async () => {
  await cargarUsuarios();
  configurarBuscador();
  configurarEdicion();
});

// ── Lista de usuarios ─────────────────────────────────────────────────────────

async function cargarUsuarios() {
  const lista = document.getElementById("lista-usuarios");
  lista.innerHTML = '<p class="status-message">Cargando...</p>';
  try {
    const res = await usuariosApi.listar();
    _todos = Array.isArray(res) ? res : (res?.contenido ?? res?.content ?? []);
    renderLista(_todos);
  } catch (err) {
    lista.innerHTML = '<p class="status-message">Error al cargar usuarios.</p>';
  }
}

function renderLista(usuarios) {
  const lista    = document.getElementById("lista-usuarios");
  const contador = document.getElementById("usuarios-contador");
  if (contador) contador.textContent = `${usuarios.length} usuario${usuarios.length !== 1 ? "s" : ""}`;

  if (!usuarios.length) {
    lista.innerHTML = `
      <div class="empty-state">
        <span class="material-symbols-outlined empty-state-icon">person_off</span>
        <h4>Sin resultados</h4>
        <p>No se encontraron usuarios con ese criterio.</p>
      </div>`;
    return;
  }

  lista.innerHTML = usuarios.map(u => {
    const activo = u.activo !== false;
    return `
    <div class="gestion-fila" id="urow-${u.id}">
      <div class="gestion-fila-info">
        <div>
          <strong style="display:block;color:#1f2937;">${escapeHtml(u.nombre || "Sin nombre")}</strong>
          <span style="font-size:13px;color:#6b7280;">${escapeHtml(u.email)}</span>
        </div>
        <span class="badge" style="font-size:11px;padding:3px 10px;border-radius:999px;font-weight:600;background:${activo?"#d1fae5":"#fee2e2"};color:${activo?"#065f46":"#991b1b"};">
          ${activo ? "Activo" : "Inactivo"}
        </span>
      </div>
      <div class="gestion-fila-acciones">
        <button class="btn-fila btn-fila--edit btn-editar" data-id="${u.id}" data-nombre="${escapeHtml(u.nombre||"")}">
          <span class="material-symbols-outlined">edit</span>Editar
        </button>
        ${activo
          ? `<button class="btn-fila btn-fila--danger btn-desactivar" data-id="${u.id}"><span class="material-symbols-outlined">person_off</span>Desactivar</button>`
          : `<button class="btn-fila btn-fila--success btn-activar" data-id="${u.id}"><span class="material-symbols-outlined">person_check</span>Activar</button>`}
      </div>
    </div>`;
  }).join("");

  lista.querySelectorAll(".btn-editar").forEach(b =>
    b.addEventListener("click", () => abrirEdicion(b.dataset.id, b.dataset.nombre)));
  lista.querySelectorAll(".btn-activar, .btn-desactivar").forEach(b =>
    b.addEventListener("click", () => toggleEstado(b.dataset.id, b.classList.contains("btn-activar"))));
}

// ── Buscador ──────────────────────────────────────────────────────────────────

function configurarBuscador() {
  document.getElementById("buscador-usuarios")?.addEventListener("input", e => {
    const txt = e.target.value.toLowerCase().trim();
    renderLista(txt ? _todos.filter(u =>
      (u.nombre||"").toLowerCase().includes(txt) ||
      (u.email||"").toLowerCase().includes(txt)
    ) : _todos);
  });
}

// ── Edición de nombre ─────────────────────────────────────────────────────────

function abrirEdicion(id, nombre) {
  const sec = document.getElementById("seccion-editar");
  document.getElementById("edit-usuario-id").value = id;
  document.getElementById("edit-nombre").value      = nombre;
  document.getElementById("edit-exito").style.display = "none";
  document.getElementById("edit-error").style.display = "none";
  sec.style.display = "block";
  sec.scrollIntoView({ behavior: "smooth", block: "start" });
  document.getElementById("edit-nombre").focus();
}

function configurarEdicion() {
  document.getElementById("btn-cancelar-edicion")?.addEventListener("click", () => {
    document.getElementById("seccion-editar").style.display = "none";
  });

  document.getElementById("btn-guardar-nombre")?.addEventListener("click", async () => {
    const id     = document.getElementById("edit-usuario-id").value;
    const nombre = document.getElementById("edit-nombre").value.trim();
    const errEl  = document.getElementById("err-edit-nombre");
    const exitoEl = document.getElementById("edit-exito");
    const errorEl = document.getElementById("edit-error");
    exitoEl.style.display = "none";
    errorEl.style.display = "none";
    if (errEl) errEl.textContent = "";

    if (!nombre) { if (errEl) errEl.textContent = "El nombre no puede estar vacío."; return; }

    const btn = document.getElementById("btn-guardar-nombre");
    btn.disabled = true;
    try {
      await usuariosApi.actualizar(id, { nombre });
      const u = _todos.find(x => String(x.id) === String(id));
      if (u) u.nombre = nombre;
      exitoEl.style.display = "block";
      // Actualiza el nombre en la fila sin recargar
      const row = document.getElementById(`urow-${id}`);
      if (row) {
        const nombreDiv = row.querySelector("div > div");
        if (nombreDiv) nombreDiv.textContent = nombre;
        row.querySelector(".btn-editar")?.setAttribute("data-nombre", escapeHtml(nombre));
      }
    } catch (err) {
      errorEl.style.display = "block";
      errorEl.textContent = "Error: " + (err.message || "No se pudo actualizar.");
    } finally {
      btn.disabled = false;
    }
  });
}

// ── Activar / Desactivar ──────────────────────────────────────────────────────

async function toggleEstado(id, activar) {
  if (!confirm(activar ? "¿Activar este usuario?" : "¿Desactivar este usuario?")) return;
  const row = document.getElementById(`urow-${id}`);
  if (row) { row.style.opacity = "0.5"; row.style.pointerEvents = "none"; }
  try {
    if (activar) await usuariosApi.activar(id); else await usuariosApi.desactivar(id);
    const u = _todos.find(x => String(x.id) === String(id));
    if (u) u.activo = activar;
    mostrarToast(`Usuario ${activar ? "activado" : "desactivado"} correctamente.`, "exito");
    const buscador = document.getElementById("buscador-usuarios");
    const txt = buscador?.value.toLowerCase().trim();
    renderLista(txt ? _todos.filter(u =>
      (u.nombre||"").toLowerCase().includes(txt) || (u.email||"").toLowerCase().includes(txt)
    ) : _todos);
  } catch (err) {
    mostrarToast("Error: " + (err.message || "No se pudo cambiar el estado."), "error");
    if (row) { row.style.opacity = "1"; row.style.pointerEvents = "all"; }
  }
}
